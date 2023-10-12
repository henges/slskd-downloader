package dev.polluxus.spotify_offline_playlist;

import au.com.muel.envconfig.EnvConfig;
import dev.polluxus.spotify_offline_playlist.client.slskd.request.SlskdDownloadRequest;
import dev.polluxus.spotify_offline_playlist.client.slskd.response.SlskdSearchDetailResponse;
import dev.polluxus.spotify_offline_playlist.client.slskd.response.SlskdSearchDetailResponse.SlskdSearchMatchResponse;
import dev.polluxus.spotify_offline_playlist.model.AlbumInfo;
import dev.polluxus.spotify_offline_playlist.model.Playlist;
import dev.polluxus.spotify_offline_playlist.model.Playlist.PlaylistAlbum;
import dev.polluxus.spotify_offline_playlist.model.Playlist.PlaylistSong;
import dev.polluxus.spotify_offline_playlist.service.SlskdService;
import dev.polluxus.spotify_offline_playlist.service.SpotifyService;
import dev.polluxus.spotify_offline_playlist.util.TriFunction;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
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

        DownloadConfirmer downloadConfirmer = new DownloadConfirmer(slskdService);

        List<CompletableFuture<List<SlskdSearchDetailResponse>>> requestsInFlight = new ArrayList<>();
        final Map<String, CompletableFuture<List<SlskdSearchDetailResponse>>> allRequests = new HashMap<>();
        for (var ai : albumInfos) {
            if (requestsInFlight.size() >= 5) {
                CompletableFuture.allOf(requestsInFlight.toArray(CompletableFuture[]::new)).join();
                requestsInFlight = new ArrayList<>();
            }
            final var future = slskdService.search(ai)
                    .thenApply(l -> {
                        Map<Integer, List<SlskdSearchDetailResponse>> scoredResps = l.stream()
                                .collect(Collectors.toMap(
                                        r -> Heuristics.score(r, ai.tracks()),
                                        r -> {
                                            List<SlskdSearchDetailResponse> ret = new ArrayList<>();
                                            ret.add(r);
                                            return ret;
                                        },
                                        (o, n) -> {
                                            o.addAll(n);
                                            return o;
                                        }));

                        List<SlskdSearchDetailResponse> sorted = scoredResps.entrySet().stream()
                                .sorted(Entry.comparingByKey())
                                .flatMap(e -> e.getValue().stream())
                                .toList();
                        return sorted;
                    })
                    .whenComplete((l, t) -> downloadConfirmer.accept(ai.name() + " " + String.join(", ", ai.artists()), l));
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            requestsInFlight.add(future);
            allRequests.put(ai.name() + " " + String.join(", ", ai.artists()), future);
        }
        CompletableFuture.allOf(allRequests.values().toArray(CompletableFuture[]::new)).join();
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
            if (bitrates.isEmpty()) {
                return false;
            }
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

    public static class DownloadConfirmer {

        private final Deque<Pair<String, List<SlskdSearchDetailResponse>>> queue;
        private final Scanner scanner;
        private final SlskdService service;

        private boolean isLocked = false;

        public DownloadConfirmer(
                SlskdService service) {
            this.queue = new ArrayDeque<>();
            this.scanner = new Scanner(System.in);
            this.service = service;
        }

        public synchronized void accept(final String query, final List<SlskdSearchDetailResponse> results) {

            final boolean wasEmpty = queue.isEmpty();
            queue.addLast(Pair.of(query, results));
            if (wasEmpty && !isLocked) {
                isLocked = true;
                CompletableFuture.runAsync(this::confirm)
                        .whenComplete((_1, _2) -> isLocked = false);
            }
        }

        public void confirm() {
            while (!queue.isEmpty()) {

                final var res = queue.pop();
                final String searchString = res.getKey();
                final List<SlskdSearchDetailResponse> results = res.getValue();
                System.out.println("For query " + searchString);
                if (results.isEmpty()) {
                    log.info("No good results for this query :\\");
                    continue;
                }

                for (var e : results) {
                    System.out.println("Will download these files from " + e.username() +": ");
                    for (var f : e.files()) {
                        System.out.printf("\t%s\n", f.filename());
                    }
                    System.out.println("OK? [y/n/skip]");
                    boolean responseOk = false;
                    String response;
                    do {
                        response = scanner.nextLine();
                        switch (response) {
                            case "y", "n", "skip" -> responseOk = true;
                            default -> System.out.println("Invalid response");
                        }
                    } while (!responseOk);
                    if (response.equals("n")) {
                        continue;
                    }
                    if (response.equals("skip")) {
                        break;
                    }
                    boolean ok = service.initiateDownloads(e.username(), e.files().stream().map(f -> new SlskdDownloadRequest(f.filename(), f.size())).toList());
                    if (ok) {
                        log.info("initiated download for {} from {} OK.", searchString, e.username());
                        break;
                    } else {
                        log.info("Failed to download {} from {}... trying next result", searchString, e.username());
                    }
                }
            }
        }
    }
}
