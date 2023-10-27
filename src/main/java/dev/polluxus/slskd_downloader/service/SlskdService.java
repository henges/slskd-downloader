package dev.polluxus.slskd_downloader.service;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import dev.polluxus.slskd_downloader.client.slskd.response.SlskdGetDownloadResponse;
import dev.polluxus.slskd_downloader.config.Config;
import dev.polluxus.slskd_downloader.client.slskd.SlskdClient;
import dev.polluxus.slskd_downloader.client.slskd.request.SlskdDownloadRequest;
import dev.polluxus.slskd_downloader.client.slskd.response.SlskdSearchDetailResponse;
import dev.polluxus.slskd_downloader.client.slskd.response.SlskdSearchStateResponse;
import dev.polluxus.slskd_downloader.model.AlbumInfo;
import dev.polluxus.slskd_downloader.model.UserAndFile;
import dev.polluxus.slskd_downloader.util.FutureUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SlskdService {

    private static final Logger log = LoggerFactory.getLogger(SlskdService.class);

    // Package-private constants, may be used in tests
    static final int SEARCH_POOL_SIZE = 5;
    static final int MAX_CONCURRENT_ACTIVE_DOWNLOADS = 30;
    static final int MAX_RETRIES = 10;

    private final SlskdClient client;

    private final ExecutorService searchPool;
    private final List<CompletableFuture<?>> searchQueue;
    private final Map<String, SlskdSearchStateResponse> allSearchStates;

    private final AtomicBoolean canDownload;
    private final Object canDownloadLock = new Object();
    private final ScheduledExecutorService downloadExecutor;
    private final ExecutorService virtualThreadExecutor;

    private List<SlskdGetDownloadResponse> downloadsList;
    private final Map<String, Integer> retryCounts;

    public SlskdService(SlskdClient client) {

        this.client = client;
        this.searchPool = Executors.newFixedThreadPool(SEARCH_POOL_SIZE);
        this.searchQueue = new ArrayList<>(SEARCH_POOL_SIZE);
        this.allSearchStates = client.getAllSearchStates()
                .stream()
                .collect(Collectors.toMap(
                        SlskdSearchStateResponse::searchText,
                        Function.identity(),
                        // Searches are returned earliest -> latest, so use the freshest
                        // result only
                        (o1, o2) -> o2
                ));
        this.canDownload = new AtomicBoolean(true);
        this.downloadExecutor = new ScheduledThreadPoolExecutor(2);
        this.downloadsList = new ArrayList<>();
        this.retryCounts = new ConcurrentHashMap<>();
        this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public SlskdService(Config config) {
        this(SlskdClient.create(config));
    }

    private boolean started = false;

    /**
     * Allows starting the service separately to instantiation
     * @return this object (for chaining)
     */
    @CanIgnoreReturnValue
    public SlskdService start() {
        if (started) {
            return this;
        }
        started = true;

        Runnable checkCanDownload = () -> {
            synchronized (canDownloadLock) {
                final boolean oldValue = canDownload.get();
                // Impose a maximum of 30 concurrent downloads from different users. This arbitrary number
                // is picked as a good candidate for saturating the connection.
                this.downloadsList = client.getAllDownloads();
                final boolean newValue = downloadsList
                        .stream().filter(r -> r.directories().stream()
                                .anyMatch(d -> d.files().stream()
                                        .anyMatch(f -> f.state().contains("InProgress"))))
                        .toList()
                        .size() < MAX_CONCURRENT_ACTIVE_DOWNLOADS;
                canDownload.set(newValue);
                // If the value transitioned from false to true, wakeup the waiters
                if (!oldValue && newValue) {
                    canDownloadLock.notifyAll();
                }
            }
        };
        Runnable retryDownloads = () -> {
            synchronized (canDownloadLock) {
                this.downloadsList.stream()
                        .flatMap(dr -> dr.directories().stream()
                                .flatMap(d -> d.files().stream())
                                .filter(f -> "Completed, Errored".equals(f.state()))
                                .map(f -> new UserAndFile(dr.username(), f)))
                        .collect(Collectors.groupingBy(UserAndFile::username))
                        .forEach((u, fs) -> {

                            final List<SlskdDownloadRequest> toRetry = new ArrayList<>();

                            for (var fd: fs) {
                                final int retryCount = retryCounts.computeIfAbsent(fd.asKey(), k -> 0);
                                if (retryCount <= MAX_RETRIES) {
                                    toRetry.add(new SlskdDownloadRequest(fd.file().filename(), fd.file().size()));
                                    retryCounts.put(fd.asKey(), retryCount + 1);
                                }
                            }

                            // Just dispatch the request, don't bother waiting for it.
                            // But we do want to time it out before the next execution of this func.
                            CompletableFuture.runAsync(() -> client.initiateDownloads(u, toRetry), virtualThreadExecutor)
                                    .orTimeout(30000, TimeUnit.MILLISECONDS);
                        });
            }
        };
        // Run it once inline, blocking the calling thread, to initialise 'can download' properly
        checkCanDownload.run();
        // Run all other invocations on the executor
        downloadExecutor.scheduleAtFixedRate(checkCanDownload, 1000, 1000, TimeUnit.MILLISECONDS);
        downloadExecutor.scheduleAtFixedRate(retryDownloads, 60000, 60000, TimeUnit.MILLISECONDS);
        return this;
    }

    public CompletableFuture<List<SlskdSearchDetailResponse>> search(final AlbumInfo albumInfo) {

        return search(albumInfo.searchString());
    }

    public CompletableFuture<List<SlskdSearchDetailResponse>> search(final String searchString) {

        // Wait for the download limit
        ensureCanDownload();

        // Once we've submitted the task limit, wait for everything to complete, then
        // wait an additional five seconds to reduce load on the network
        if (searchQueue.size() == SEARCH_POOL_SIZE) {
            CompletableFuture.allOf(searchQueue.toArray(CompletableFuture[]::new)).join();
            FutureUtils.sleep(5000);
            searchQueue.clear();
        }

        final var future = CompletableFuture.supplyAsync(() -> {
            // If there's already an exact match for this search string, don't execute the search
            // again. This is useful mostly for development and could probably be removed once
            // the program is more stable.
            final SlskdSearchStateResponse initResp;
            if (allSearchStates.containsKey(searchString)) {
                log.info("Found existing search for string {}", searchString);
                initResp = allSearchStates.get(searchString);
            } else {
                log.info("Creating new search for {}", searchString);
                initResp = client.search(searchString);
            }

            // Poll the server every second until we get a 'Completed' state. After 45 polls,
            // give up on the request.
            int pollCount = 0;
            String state = initResp.state();
            while (!state.contains("Completed")) {
                if (pollCount++ > 45) {
                    throw new RuntimeException(STR."Timed out search \"\{searchString}\" after 45 seconds");
                }
                FutureUtils.sleep(1000);
                final var currState = client.getSearchState(initResp.id());
                state = currState.state();
            }

            var responses = client.getSearchResponses(initResp.id());

            log.info("Got {} responses for query {}", responses.size(), searchString);

            return responses;
        }, this.searchPool)
        .exceptionally(t -> {
            log.error("Exception while retrieving search for {}", searchString, t);
            return List.of();
        });

        searchQueue.add(future);

        return future;
    }

    private void ensureCanDownload() {

        synchronized (canDownloadLock) {
            while (!canDownload.get()) {
                try {
                    canDownloadLock.wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public boolean initiateDownloads(final String hostUser, final List<SlskdDownloadRequest> files) {
        ensureCanDownload();

        try {
            client.initiateDownloads(hostUser, files);
            return true;
        } catch (Exception e) {
            log.error("Error starting download request from user {} for {}", hostUser, files, e);
            return false;
        }
    }

    public void shutdown() {
        this.searchPool.shutdownNow();
        this.downloadExecutor.shutdownNow();
        this.virtualThreadExecutor.shutdownNow();
        try {
            this.searchPool.awaitTermination(15000, TimeUnit.MILLISECONDS);
            this.downloadExecutor.awaitTermination(15000, TimeUnit.MILLISECONDS);
            this.virtualThreadExecutor.awaitTermination(15000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            log.error("Error terminating SlskdService worker pool", e);
            throw new RuntimeException(e);
        }
    }
}
