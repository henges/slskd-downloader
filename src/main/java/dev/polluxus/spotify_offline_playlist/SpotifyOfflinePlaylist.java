package dev.polluxus.spotify_offline_playlist;

import au.com.muel.envconfig.EnvConfig;
import dev.polluxus.spotify_offline_playlist.client.musicbrainz.MusicbrainzClient;
import dev.polluxus.spotify_offline_playlist.client.musicbrainz.dto.MusicbrainzReleaseSearchResult;
import dev.polluxus.spotify_offline_playlist.client.slskd.request.SlskdDownloadRequest;
import dev.polluxus.spotify_offline_playlist.config.Config;
import dev.polluxus.spotify_offline_playlist.model.AlbumInfo;
import dev.polluxus.spotify_offline_playlist.model.Playlist;
import dev.polluxus.spotify_offline_playlist.model.Playlist.PlaylistAlbum;
import dev.polluxus.spotify_offline_playlist.model.Playlist.PlaylistSong;
import dev.polluxus.spotify_offline_playlist.processor.matcher.MatchStrategyType;
import dev.polluxus.spotify_offline_playlist.processor.SlskdResponseProcessor;
import dev.polluxus.spotify_offline_playlist.processor.model.ProcessorSearchResult;
import dev.polluxus.spotify_offline_playlist.service.SlskdService;
import dev.polluxus.spotify_offline_playlist.service.SpotifyService;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Supplier;

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

        final SlskdResponseProcessor processor = new SlskdResponseProcessor(MatchStrategyType.EDIT_DISTANCE);
        final DownloadConfirmer downloadConfirmer = new DownloadConfirmer(slskdService);
        final Iterator<AlbumInfo> albumInfos = spotifyPlaylistSupplier(spotifyService, playlistId);

        processLoop(albumInfos, slskdService, processor, downloadConfirmer);
    }

    public static Iterator<AlbumInfo> spotifyPlaylistSupplier(final SpotifyService spotifyService, final String playlistId) {

        final Playlist p = spotifyService.getPlaylist(playlistId);
        final List<PlaylistAlbum> distinctPlaylistAlbums = p.tracks().stream().map(PlaylistSong::playlistAlbum).distinct().toList();
        List<AlbumInfo> ais = spotifyService.getAlbums(distinctPlaylistAlbums.stream().map(PlaylistAlbum::spotifyId).toList());
        return ais.iterator();
    }

    public static Iterator<AlbumInfo> musicbrainzFileSupplier(final MusicbrainzClient client, final List<Pair<String, String>> artistAlbumPairs) {

        final Iterator<Pair<String, String>> artistAlbumPairIt = artistAlbumPairs.iterator();

        return new Iterator<>() {

            AlbumInfo next;

            @Override
            public boolean hasNext() {
                if (!artistAlbumPairIt.hasNext()) {
                    return false;
                }
                final Pair<String, String> currPair = artistAlbumPairIt.next();
                MusicbrainzReleaseSearchResult r = client.searchReleases(currPair.getLeft(), currPair.getRight());
                if (r.releases().size() == 0 && !artistAlbumPairIt.hasNext()) {
                    return false;
                }
                final var bestMatch = r.releases().get(0);
                final String artistName = bestMatch.artistCredit().get(0).artist().name();
                final String albumName = bestMatch.title();
                // TODO

                return true;
            }

            @Override
            public AlbumInfo next() {

                return null;
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

    public static class DownloadConfirmer implements Function<ProcessorSearchResult, CompletableFuture<Void>> {

        private final BlockingQueue<Pair<ProcessorSearchResult, CompletableFuture<Void>>> queue;
        private final Scanner scanner;
        private final SlskdService service;
        private final ExecutorService executor;

        private DownloadConfirmer(
                SlskdService service) {
            this.queue = new ArrayBlockingQueue<>(500);
            this.scanner = new Scanner(System.in);
            this.service = service;
            this.executor = Executors.newFixedThreadPool(1);
        }

        public static DownloadConfirmer start(SlskdService service) {

            var dc = new DownloadConfirmer(service);
            dc.executor.submit(dc::confirm);
            return dc;
        }

        public void stop() {
            this.executor.shutdown();
            try {
                this.executor.awaitTermination(15000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        public CompletableFuture<Void> apply(ProcessorSearchResult result) {

            CompletableFuture<Void> cf = new CompletableFuture<>();
            queue.offer(Pair.of(result, cf));
            return cf;
        }

        private void confirm() {
            while (true) {
                final Pair<ProcessorSearchResult, CompletableFuture<Void>> pair;
                try {
                    pair = queue.take();
                } catch (InterruptedException e) {
                    log.error("Was interrupted", e);
                    continue;
                }
                final var res = pair.getKey();
                final var future = pair.getValue();
                final String searchString = res.albumInfo().searchString();
                System.out.println("For query " + searchString);
                if (res.userResults().isEmpty()) {
                    log.info("No good results for this query :\\");
                    continue;
                }

                for (var e : res.userResults()) {
                    System.out.println("Will download these files from " + e.username() +": ");
                    for (var f : e.bestCandidates()) {
                        System.out.printf("\t%s\n", f.originalData().filename());
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
                    boolean ok = service.initiateDownloads(e.username(), e.bestCandidates().stream()
                            .map(f -> new SlskdDownloadRequest(f.originalData().filename(), f.originalData().size())).toList());
                    if (ok) {
                        log.info("initiated download for {} from {} OK.", searchString, e.username());
                        break;
                    } else {
                        log.info("Failed to download {} from {}... trying next result", searchString, e.username());
                    }
                }
                future.complete(null);
            }
        }
    }
}
