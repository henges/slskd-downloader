package dev.polluxus.slskd_downloader.processor;

import dev.polluxus.slskd_downloader.client.slskd.request.SlskdDownloadRequest;
import dev.polluxus.slskd_downloader.confirmer.TerminalConfirmer;
import dev.polluxus.slskd_downloader.model.AlbumInfo;
import dev.polluxus.slskd_downloader.processor.DownloadProcessor.DownloadResult;
import dev.polluxus.slskd_downloader.processor.model.ProcessorSearchResult;
import dev.polluxus.slskd_downloader.processor.model.ProcessorUserResult;
import dev.polluxus.slskd_downloader.service.SlskdService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public class DownloadProcessor implements Function<ProcessorSearchResult, CompletableFuture<DownloadResult>> {

    public interface DownloadConfirmer {
        UserConfirmationResult confirm(final AlbumInfo albumInfo, final ProcessorUserResult res);
        void informSuccess(AlbumInfo ai, String username);
        void informFailure(AlbumInfo ai, String username);
        void shutdown();
    }

    private static final Logger log = LoggerFactory.getLogger(DownloadProcessor.class);

    private final SlskdService service;
    private final ExecutorService executor;
    private final DownloadConfirmer confirmer;

    public DownloadProcessor(
            SlskdService service) {
        this.service = service;
        this.executor = Executors.newFixedThreadPool(1);
        this.confirmer = new TerminalConfirmer();
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

    public enum UserConfirmationResult {
        YES,
        NO,
        SKIP
    }

    public enum DownloadResult {
        OK,
        TRIED_FAILED,
        EXPLICITLY_SKIPPED,
        DIDNT_TRY
    }

    public CompletableFuture<DownloadResult> apply(ProcessorSearchResult res) {

        AtomicReference<DownloadResult> downloadResult = new AtomicReference<>(DownloadResult.DIDNT_TRY);

        return CompletableFuture.runAsync(() -> {

            if (res.userResults().isEmpty()) {
                log.info("No good results for this query :\\");
                return;
            }

            resultsLoop: for (var e : res.userResults()) {
                switch (confirmer.confirm(res.albumInfo(), e)) {
                    case NO -> {
                        continue;
                    }
                    case SKIP -> {
                        downloadResult.set(DownloadResult.EXPLICITLY_SKIPPED);
                        break resultsLoop;
                    }
                    case YES -> {
                        boolean ok = service.initiateDownloads(e.username(), e.bestCandidates().stream()
                                .map(f -> new SlskdDownloadRequest(f.originalData().filename(), f.originalData().size()))
                                .toList());

                        if (ok) {
                            downloadResult.set(DownloadResult.OK);
                            confirmer.informSuccess(res.albumInfo(), e.username());
                            break resultsLoop;
                        } else {
                            downloadResult.set(DownloadResult.TRIED_FAILED);
                            confirmer.informFailure(res.albumInfo(), e.username());
                        }
                    }
                }
            }
        }, executor)
        .thenApply(__ -> downloadResult.get());
    }
}
