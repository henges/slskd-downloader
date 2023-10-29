package dev.polluxus.slskd_downloader.processor;

import dev.polluxus.slskd_downloader.client.slskd.request.SlskdDownloadRequest;
import dev.polluxus.slskd_downloader.client.slskd.response.SlskdGetDownloadResponse.SlskdDownloadFileResponse;
import dev.polluxus.slskd_downloader.config.ThreadPoolConfig;
import dev.polluxus.slskd_downloader.model.AlbumInfo;
import dev.polluxus.slskd_downloader.processor.DownloadProcessor.DownloadResult;
import dev.polluxus.slskd_downloader.processor.model.output.ProcessorSearchResult;
import dev.polluxus.slskd_downloader.processor.model.output.ProcessorUserResult;
import dev.polluxus.slskd_downloader.service.SlskdService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ActiveDownloadProcessor implements Function<ProcessorSearchResult, CompletableFuture<DownloadResult>> {

    private static final Logger log = LoggerFactory.getLogger(ActiveDownloadProcessor.class);

    private List<ProcessorUserResult> userResults;
    private AlbumInfo albumInfo;
    private int position;
    private DownloadTracker tracker;
    private boolean locked;

    private final SlskdService slskdService;
    private final CompletableFuture<DownloadResult> result;
    /**
     * This is a bit of a smell, but the processor uses the callback function as part of the
     * identity of the subscription. And because two references to the same method are not equal, e.g.
     * <pre>
     *     String s = "a";
     *     var m1 = s::equals;
     *     var m2 = s::equals;
     *     return m1.equals(m2);
     * </pre>
     * returns false, we therefore must maintain a reference to only a single instance of this function.
     */
    private final Consumer<List<SlskdDownloadFileResponse>> onUpdate;

    private static class DownloadTracker {
        private final ProcessorUserResult target;
        private int failureCount = 0;
        private int timesWhereEmpty = 0;

        public DownloadTracker(ProcessorUserResult target) {
            this.target = target;
        }
    }

    public ActiveDownloadProcessor(SlskdService slskdService) {
        this.result = new CompletableFuture<>();
        this.position = 0;
        this.slskdService = slskdService;
        this.locked = false;
        this.onUpdate = (files) -> {
            if (locked) {
                return;
            }

            locked = true;
            try {
                onUpdateBody(files);
            } finally {
                locked = false;
            }
        };
    }

    private void onUpdateBody(List<SlskdDownloadFileResponse> files) {

        DownloadTracker current = this.tracker;

        if (current == null) {
            log.warn("Outcome for download result {} of search {}: tracker gone!", this.position, this.albumInfo.searchString());
            this.unsubscribe();
            this.process();
            return;
        }
        final String hostUser = current.target.username();

        // If there aren't any matching files, seems likely that our download
        // never successfully started, but give it a bit anyways
        if (files.isEmpty()) {
            current.timesWhereEmpty++;
            // Once this has fired a few times, abandon this request
            if (current.timesWhereEmpty >= 5) {
                log.warn("Outcome for download result {} of search {}: was empty for too long", this.position, this.albumInfo.searchString());
                this.unsubscribe();
                this.process();
                return;
            }
        }

        // If every file completed OK, then we are done, yippee!!
        if (files.stream().allMatch(dfr -> "Completed, Succeeded".equals(dfr.state()))) {
            log.info("Outcome for download result {} of search {}: finished OK!", this.position, this.albumInfo.searchString());
            this.unsubscribe();
            result.complete(DownloadResult.OK);
            return;
        }

        // If any files are in progress, we don't need to do anything, just keep waiting!
        if (files.stream().anyMatch(dfr -> "InProgress".equals(dfr.state()))) {
            log.info("Outcome for download result {} of search {}: still in progress and looking good", this.position, this.albumInfo.searchString());
            return;
        }

        if (files.size() > albumInfo.tracks().size()) {
            log.info("Looks like there may have been a duplicate transfer request created for download for {} from user {}... Going to deduplicate them",
                    albumInfo.searchString(), hostUser);
            final var transfersToRemove = files.stream()
                    // Group them by filename
                    .collect(Collectors.groupingBy(SlskdDownloadFileResponse::filename))
                    .values().stream()
                    // Find the ones where the list size is greater than one
                    .filter(ls -> ls.size() > 1)
                    // Remove the first element from these lists
                    .peek(List::removeFirst)
                    .flatMap(Collection::stream)
                    .map(SlskdDownloadFileResponse::id)
                    .toList();
            cancelAndRemoveAll(hostUser, transfersToRemove);
            log.info("Outcome for download result {} of search {}: removed {} duplicate transfers", this.position, this.albumInfo.searchString(), transfersToRemove.size());
            return;
        }

        // Find all the failed files
        var failures = files.stream().filter(dfr -> "Completed, Errored".equals(dfr.state())).toList();

        if (failures.isEmpty()) {
            log.info("Outcome for download result {} of search {}: enqueued...", this.position, this.albumInfo.searchString());
            return;
        }

        // If we're over the failure threshold, unsubscribe, cancel these downloads, and proceed with the next result
        current.failureCount += failures.size();
        if (current.failureCount / files.size() >= 10) {
            log.warn("Outcome for download result {} of search {}: exceeded failure threshold", this.position, this.albumInfo.searchString());
            this.unsubscribe();
            this.cancelAndRemoveAll(hostUser, files.stream().map(SlskdDownloadFileResponse::id).toList());
            this.process();
            return;
        }
        // Otherwise, retry them all
        retryAll(hostUser, failures);
        log.info("Outcome for download result {} of search {}: retrying {} files", this.position, this.albumInfo.searchString(), failures.size());
    }

    private void unsubscribe() {
        slskdService.unsubscribe(this.tracker.target.username(), this.tracker.target.bestCandidates().stream()
                .map(fr -> new SlskdDownloadRequest(fr.originalData().filename(), fr.originalData().size()))
                .toList(), onUpdate);
        this.tracker = null;
    }

    private void retryAll(final String hostUser, final List<SlskdDownloadFileResponse> files) {

        slskdService.initiateDownloads(hostUser, files.stream().map(f -> new SlskdDownloadRequest(f.filename(), f.size())).toList());
    }

    private void cancelAll(final String hostUser, List<UUID> fileIds) {

        slskdService.cancelDownloads(hostUser, fileIds, false);
    }

    private void cancelAndRemoveAll(final String hostUser, List<UUID> fileIds) {

        slskdService.cancelDownloads(hostUser, fileIds, true);
    }

    public void process() {

        CompletableFuture.runAsync(() -> {

            // If it's empty right off the bat, then there's just nothing to do.
            if (userResults.isEmpty()) {

                result.complete(DownloadResult.DIDNT_TRY);
            }

            if (position < userResults.size()) {
                final var currResult = userResults.get(position++);
                this.tracker = new DownloadTracker(currResult);
                log.info("Enqueueing download with {} ({} files) for search {}", currResult.username(), currResult.bestCandidates().size(), albumInfo.searchString());
                slskdService.initiateAndSubscribe(currResult.username(), currResult.bestCandidates().stream()
                        .map(fr -> new SlskdDownloadRequest(fr.originalData().filename(), fr.originalData().size()))
                        .toList(), this.onUpdate);
                return;
            }
            // If a tracker wasn't set, we escaped the loop above without starting a download,
            // and we have run out of results
            if (this.tracker == null) {
                result.complete(DownloadResult.TRIED_FAILED);
            }
        }, ThreadPoolConfig.VIRTUAL_THREAD_EXECUTOR);
    }

    @Override
    public CompletableFuture<DownloadResult> apply(ProcessorSearchResult processorSearchResult) {

        this.userResults = processorSearchResult.userResults().stream()
                .filter(f -> f.scoreOfBestCandidates() >= 0.8 && f.bestCandidates().size() == processorSearchResult.albumInfo().tracks().size())
                .toList();
        this.albumInfo = processorSearchResult.albumInfo();
        this.process();
        return result;
    }
}
