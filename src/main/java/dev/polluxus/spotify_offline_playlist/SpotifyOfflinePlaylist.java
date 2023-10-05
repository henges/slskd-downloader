package dev.polluxus.spotify_offline_playlist;

import au.com.muel.envconfig.EnvConfig;
import dev.polluxus.spotify_offline_playlist.client.musicbrainz.MusicbrainzClient;
import dev.polluxus.spotify_offline_playlist.client.musicbrainz.dto.MusicbrainzReleaseSearchResult;
import dev.polluxus.spotify_offline_playlist.client.slskd.SlskdClient;
import dev.polluxus.spotify_offline_playlist.client.slskd.response.SlskdSearchDetailResponse;
import dev.polluxus.spotify_offline_playlist.client.slskd.response.SlskdSearchDetailResponse.SlskdSearchMatchResponse;
import dev.polluxus.spotify_offline_playlist.model.AlbumInfo;
import dev.polluxus.spotify_offline_playlist.model.Playlist;
import dev.polluxus.spotify_offline_playlist.model.Playlist.PlaylistAlbum;
import dev.polluxus.spotify_offline_playlist.model.Playlist.PlaylistSong;
import dev.polluxus.spotify_offline_playlist.service.SlskdService;
import dev.polluxus.spotify_offline_playlist.service.SpotifyService;
import dev.polluxus.spotify_offline_playlist.util.TriFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SpotifyOfflinePlaylist {

    private static final Logger log = LoggerFactory.getLogger(SpotifyOfflinePlaylist.class);

    public static void main(String[] args) {

        final Config config = EnvConfig.fromEnv(Config.class);
        final String playlistId = config.spotifyPlaylistId();
        final SpotifyService spotifyService = new SpotifyService(config);
        final SlskdService slskdService = new SlskdService(config);
        process(playlistId, spotifyService, slskdService);
    }

    public static void process(String playlistId, SpotifyService spotifyService, SlskdService slskdService) {

        final Playlist p = spotifyService.getPlaylist(playlistId);
        final List<PlaylistAlbum> distinctPlaylistAlbums = p.tracks().stream().map(PlaylistSong::playlistAlbum).distinct().toList();
        final List<AlbumInfo> albumInfos = spotifyService.getAlbums(distinctPlaylistAlbums.stream().map(PlaylistAlbum::spotifyId).toList());

        List<CompletableFuture<List<SlskdSearchDetailResponse>>> requestsInFlight = new ArrayList<>();
        final List<CompletableFuture<List<SlskdSearchDetailResponse>>> allRequests = new ArrayList<>();
        for (var ai : albumInfos) {
            if (requestsInFlight.size() >= 10) {
                CompletableFuture.allOf(requestsInFlight.toArray(CompletableFuture[]::new)).join();
                requestsInFlight = new ArrayList<>();
            }
            final var future = slskdService.search(ai).thenApply(l -> {
                Map<Integer, List<SlskdSearchDetailResponse>> scoredResps = l.stream()
                        .collect(Collectors.toMap(
                                r -> Heuristics.score(r, ai.tracks()),
                                List::of,
                                (o, n) -> {
                                    o.addAll(n);
                                    return o;
                                }));

                List<SlskdSearchDetailResponse> sorted = scoredResps.entrySet().stream()
                        .sorted(Entry.comparingByKey())
                        .flatMap(e -> e.getValue().stream())
                        .toList();
                return sorted;
            });
            requestsInFlight.add(future);
            allRequests.add(future);
        }
        CompletableFuture.allOf(requestsInFlight.toArray(CompletableFuture[]::new)).join();
        allRequests


    }

    static class Heuristics {

        public static int score(SlskdSearchDetailResponse result, List<String> targetTrackNames) {

            boolean hasRightFormat = Heuristics.hasRightFormatAndLength.apply(result, "mp3", targetTrackNames.size());
            boolean hasRightBitrate = Heuristics.hasRightBitrate.apply(result, 224, "GE");
            boolean hasAllTracks = Heuristics.hasAllTracks.apply(result, targetTrackNames);

            return (hasAllTracks ? 5 : 0) + (hasRightFormat ? 3 : 0) + (hasRightBitrate ? 1 : 0);
        }

        static TriFunction<SlskdSearchDetailResponse, String, Integer, Boolean> hasRightFormatAndLength = (res, fmt, len) -> {

            Pattern fmtPattern = Pattern.compile("\\." + fmt, Pattern.CASE_INSENSITIVE);
            return res.files().stream()
                    .filter(t -> fmtPattern.matcher(t.filename()).find())
                    .toList().size() >= len;
        };

        static TriFunction<SlskdSearchDetailResponse, Integer, String, Boolean> hasRightBitrate = (res, bitrate, op) -> {

            List <Integer> bitrates = res.files().stream()
                    .filter(f -> f.bitRate().isPresent())
                    .map(f -> f.bitRate().get()).toList();
            return bitrates.stream().mapToInt(i -> i).sum() / bitrates.size() >= bitrate;
        };

        static BiFunction<SlskdSearchDetailResponse, List<String>, Boolean> hasAllTracks = (res, tgt) -> {

            final List<Pattern> patterns = tgt.stream()
                    .map(n -> Pattern.compile(n, Pattern.CASE_INSENSITIVE))
                    .toList();

            return res.files().stream().map(SlskdSearchMatchResponse::filename)
                    .allMatch(track -> patterns.stream()
                            .anyMatch(p -> p.matcher(track).find()));
        };

    }


    // Open a playlist identifier
    // Read the metadata from the playlist
    // Use a music provider to download the files (caching)
    // Organise the files in playlist order
}
