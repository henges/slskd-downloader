package dev.polluxus.spotify_offline_playlist.processor;

import dev.polluxus.spotify_offline_playlist.client.slskd.request.SlskdDownloadRequest;
import dev.polluxus.spotify_offline_playlist.confirmer.TerminalConfirmer;
import dev.polluxus.spotify_offline_playlist.model.AlbumInfo;
import dev.polluxus.spotify_offline_playlist.processor.model.ProcessorSearchResult;
import dev.polluxus.spotify_offline_playlist.processor.model.ProcessorUserResult;
import dev.polluxus.spotify_offline_playlist.service.SlskdService;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.function.Function;

public class DownloadProcessor implements Function<ProcessorSearchResult, CompletableFuture<Void>> {

    public interface DownloadConfirmer {
        UserConfirmationResult confirm(final AlbumInfo albumInfo, final ProcessorUserResult res);
        void informFailure(AlbumInfo ai, String username);
        void shutdown();
    }

    private static final Logger log = LoggerFactory.getLogger(DownloadProcessor.class);

    private final BlockingQueue<Pair<ProcessorSearchResult, CompletableFuture<Void>>> queue;
    private final SlskdService service;
    private final ExecutorService executor;
    private final DownloadConfirmer confirmer;

    private DownloadProcessor(
            SlskdService service) {
        this.queue = new ArrayBlockingQueue<>(500);
        this.service = service;
        this.executor = Executors.newFixedThreadPool(1);
        this.confirmer = new TerminalConfirmer();
    }

    public static DownloadProcessor start(SlskdService service) {

        var dc = new DownloadProcessor(service);
        dc.executor.submit(dc::confirm);
        return dc;
    }

    public void stop() {
        this.executor.shutdownNow();
        try {
            this.executor.awaitTermination(15000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        this.confirmer.shutdown();
    }

    public CompletableFuture<Void> apply(ProcessorSearchResult result) {

        CompletableFuture<Void> cf = new CompletableFuture<>();
        queue.offer(Pair.of(result, cf));
        return cf;
    }

    public enum UserConfirmationResult {
        YES,
        NO,
        SKIP
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
            if (res.userResults().isEmpty()) {
                log.info("No good results for this query :\\");
                continue;
            }

            resultsLoop: for (var e : res.userResults()) {
                switch (confirmer.confirm(res.albumInfo(), e)) {
                    case NO -> {
                        continue;
                    }
                    case SKIP -> {
                        break resultsLoop;
                    }
                    case YES -> {
                        boolean ok = service.initiateDownloads(e.username(), e.bestCandidates().stream()
                                .map(f -> new SlskdDownloadRequest(f.originalData().filename(), f.originalData().size()))
                                .toList());

                        if (ok) {
                            break resultsLoop;
                        } else {
                            confirmer.informFailure(res.albumInfo(), e.username());
                        }
                    }
                }
            }
            future.complete(null);
        }
    }
}
