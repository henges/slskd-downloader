package dev.polluxus.spotify_offline_playlist;

import au.com.muel.envconfig.EnvConfig;
import dev.polluxus.spotify_offline_playlist.client.slskd.request.SlskdDownloadRequest;
import dev.polluxus.spotify_offline_playlist.client.slskd.response.SlskdSearchDetailResponse;
import dev.polluxus.spotify_offline_playlist.model.AlbumInfo;
import dev.polluxus.spotify_offline_playlist.model.Playlist;
import dev.polluxus.spotify_offline_playlist.model.Playlist.PlaylistAlbum;
import dev.polluxus.spotify_offline_playlist.model.Playlist.PlaylistSong;
import dev.polluxus.spotify_offline_playlist.processor.model.ProcessorFileResult;
import dev.polluxus.spotify_offline_playlist.processor.SlskdResponseProcessor;
import dev.polluxus.spotify_offline_playlist.service.SlskdService;
import dev.polluxus.spotify_offline_playlist.service.SpotifyService;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

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

        List<CompletableFuture<Map<String, List<ProcessorFileResult>>>> requestsInFlight = new ArrayList<>();
        final Map<String, CompletableFuture<Map<String, List<ProcessorFileResult>>>> allRequests = new HashMap<>();
        for (var ai : albumInfos) {
            if (requestsInFlight.size() >= 5) {
                CompletableFuture.allOf(requestsInFlight.toArray(CompletableFuture[]::new)).join();
                requestsInFlight = new ArrayList<>();
            }
            final var future = slskdService.search(ai)
                    .thenApply(l -> SlskdResponseProcessor.process(l, ai))
//                    .whenComplete((l, t) -> downloadConfirmer.accept(ai.name() + " " + String.join(", ", ai.artists()), l))
                    ;
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
//            requestsInFlight.add(future);
//            allRequests.put(ai.name() + " " + String.join(", ", ai.artists()), future);
        }
        CompletableFuture.allOf(allRequests.values().toArray(CompletableFuture[]::new)).join();
    }

    public static class DownloadConfirmer {

        private final BlockingQueue<Pair<String, List<SlskdSearchDetailResponse>>> queue;
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

        public synchronized void accept(final String query, final List<SlskdSearchDetailResponse> results) {

            queue.offer(Pair.of(query, results));
        }

        private void confirm() {
            while (true) {
                final var res = queue.poll();
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
