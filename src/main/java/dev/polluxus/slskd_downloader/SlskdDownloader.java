package dev.polluxus.slskd_downloader;

import au.com.muel.envconfig.EnvConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.polluxus.slskd_downloader.client.musicbrainz.MusicbrainzClient;
import dev.polluxus.slskd_downloader.client.musicbrainz.MusicbrainzClient.SearchOptions;
import dev.polluxus.slskd_downloader.client.musicbrainz.dto.MusicbrainzRecording;
import dev.polluxus.slskd_downloader.client.musicbrainz.dto.MusicbrainzReleaseSearchResult;
import dev.polluxus.slskd_downloader.config.Config;
import dev.polluxus.slskd_downloader.config.JacksonConfig;
import dev.polluxus.slskd_downloader.model.AlbumInfo.AlbumTrack;
import dev.polluxus.slskd_downloader.processor.DownloadProcessor;
import dev.polluxus.slskd_downloader.model.AlbumArtistPair;
import dev.polluxus.slskd_downloader.model.AlbumInfo;
import dev.polluxus.slskd_downloader.model.Playlist;
import dev.polluxus.slskd_downloader.model.Playlist.PlaylistAlbum;
import dev.polluxus.slskd_downloader.model.Playlist.PlaylistSong;
import dev.polluxus.slskd_downloader.processor.matcher.MatchStrategyType;
import dev.polluxus.slskd_downloader.processor.SlskdResponseProcessor;
import dev.polluxus.slskd_downloader.processor.model.ProcessorSearchResult;
import dev.polluxus.slskd_downloader.service.SlskdService;
import dev.polluxus.slskd_downloader.service.SpotifyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

public class SlskdDownloader {

    private static final Logger log = LoggerFactory.getLogger(SlskdDownloader.class);

    public static void main(String[] args) {

        final Config config = EnvConfig.fromEnv(Config.class);
        final String playlistId = config.spotifyPlaylistId();
        final File fileSrc = new File(config.fileSource());

        final SpotifyService spotifyService = new SpotifyService(config);
        final SlskdService slskdService = new SlskdService(config);
        final MusicbrainzClient musicbrainzClient = MusicbrainzClient.create(config);
//        final Iterator<AlbumInfo> spotifySupplier = spotifyPlaylistSupplier(spotifyService, playlistId);
        final Iterator<AlbumInfo> musicbrainzSupplier = musicbrainzFileSupplier(musicbrainzClient, fileSrc);

        process(musicbrainzSupplier, slskdService);
    }

    public static void process(final Iterator<AlbumInfo> supplier, SlskdService slskdService) {

        final SlskdResponseProcessor processor = new SlskdResponseProcessor(MatchStrategyType.EDIT_DISTANCE);
        final DownloadProcessor downloadProcessor = DownloadProcessor.start(slskdService);

        processLoop(supplier, slskdService, processor, downloadProcessor);
        downloadProcessor.stop();
        slskdService.shutdown();
    }

    public static Iterator<AlbumInfo> spotifyPlaylistSupplier(final SpotifyService spotifyService, final String playlistId) {

        final Playlist p = spotifyService.getPlaylist(playlistId);
        final List<PlaylistAlbum> distinctPlaylistAlbums = p.tracks().stream().map(PlaylistSong::playlistAlbum).distinct().toList();
        List<AlbumInfo> ais = spotifyService.getAlbums(distinctPlaylistAlbums.stream().map(PlaylistAlbum::spotifyId).toList());
        return ais.iterator();
    }

    public static Iterator<AlbumInfo> musicbrainzFileSupplier(final MusicbrainzClient client, final File artistAlbumSrc) {

        final ObjectMapper mapper = JacksonConfig.MAPPER;
        final List<AlbumArtistPair> pairs;
        try {
            pairs = mapper.readValue(artistAlbumSrc, new TypeReference<>() {});
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        final Iterator<AlbumArtistPair> artistAlbumPairIt = pairs.iterator();

        return new Iterator<>() {

            AlbumInfo next;

            @Override
            public boolean hasNext() {
                if (!artistAlbumPairIt.hasNext()) {
                    return false;
                }
                final var currPair = artistAlbumPairIt.next();
                MusicbrainzReleaseSearchResult r = client.searchReleases(currPair.artist(), currPair.album(), SearchOptions.SORTED_DATE);
                if (r.releases().size() == 0) {
                    return this.hasNext();
                }
                final var bestReleaseMatch = r.releases().get(0);
                final String artistName = bestReleaseMatch.artistCredit().get(0).artist().name();
                final String albumName = bestReleaseMatch.title();
                MusicbrainzRecording recording = client.getRecording(bestReleaseMatch.id());
                if (recording.media().size() == 0) {
                    return this.hasNext();
                }
                final List<AlbumTrack> tracks = recording.media().stream()
                        .flatMap(m -> m.tracks().stream())
                        .map(t -> new AlbumTrack(t.number(), t.title()))
                        .toList();

                next = new AlbumInfo(albumName, null, tracks, List.of(artistName));

                return true;
            }

            @Override
            public AlbumInfo next() {

                final var ret = next;
                next = null;
                return ret;
            }
        };
    }

    public static void processLoop(Iterator<AlbumInfo> albumInfos,
                                   SlskdService service,
                                   SlskdResponseProcessor processor,
                                   Function<ProcessorSearchResult, CompletableFuture<Void>> consumer) {

        List<CompletableFuture<ProcessorSearchResult>> requestsInFlight = new ArrayList<>();
        final List<CompletableFuture<Void>> allRequests = new ArrayList<>();
        while (albumInfos.hasNext()) {
            final AlbumInfo ai = albumInfos.next();
            if (requestsInFlight.size() >= 5) {
                CompletableFuture.allOf(requestsInFlight.toArray(CompletableFuture[]::new)).join();
                requestsInFlight = new ArrayList<>();
            }
            final var processFuture = service.search(ai)
                    .thenApply(l -> processor.process(l, ai));
            final var doneFuture = processFuture
                    .thenCompose(consumer);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            requestsInFlight.add(processFuture);
            allRequests.add(doneFuture);
        }
        CompletableFuture.allOf(allRequests.toArray(CompletableFuture[]::new)).join();
        log.info("All requests done.");
    }
}
