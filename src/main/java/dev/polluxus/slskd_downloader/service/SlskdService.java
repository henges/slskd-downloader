package dev.polluxus.slskd_downloader.service;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import dev.polluxus.slskd_downloader.client.slskd.response.SlskdGetDownloadResponse;
import dev.polluxus.slskd_downloader.client.slskd.response.SlskdGetDownloadResponse.SlskdDownloadFileResponse;
import dev.polluxus.slskd_downloader.config.Config;
import dev.polluxus.slskd_downloader.client.slskd.SlskdClient;
import dev.polluxus.slskd_downloader.client.slskd.request.SlskdDownloadRequest;
import dev.polluxus.slskd_downloader.client.slskd.response.SlskdSearchDetailResponse;
import dev.polluxus.slskd_downloader.client.slskd.response.SlskdSearchStateResponse;
import dev.polluxus.slskd_downloader.config.ThreadPoolConfig;
import dev.polluxus.slskd_downloader.model.AlbumInfo;
import dev.polluxus.slskd_downloader.model.UserAndFile;
import dev.polluxus.slskd_downloader.util.FutureUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
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

    private final Map<String, Set<DownloadSubscription>> subscriptions;

    public SlskdService(SlskdClient client) {

        this.client = client;
        this.searchPool = Executors.newFixedThreadPool(SEARCH_POOL_SIZE);
        this.searchQueue = new ArrayList<>(SEARCH_POOL_SIZE);
        this.allSearchStates = client.getAllSearchStates()
                .stream()
                .filter(ssr -> false)
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
        this.virtualThreadExecutor = ThreadPoolConfig.VIRTUAL_THREAD_EXECUTOR;
        this.subscriptions = new ConcurrentHashMap<>();
    }

    public SlskdService(Config config) {
        this(SlskdClient.create(config));
    }

    private boolean startedDownloadPoll = false;
    private boolean startedRetrier = false;

    public SlskdService startRetrier(boolean runImmediately) {
        if (startedRetrier) {
            return this;
        }
        startedRetrier = true;

        Runnable retryDownloads = () -> {

            log.info("Beginning scheduled retry");

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
                                } else {
                                    log.info("Skipping {} from user {} because it exceeded the retry limit",
                                            fd.file().filename(), fd.username());
                                }
                            }

                            // Just dispatch the request, don't bother waiting for it.
                            // But we do want to time it out before the next execution of this func.
                            CompletableFuture.runAsync(() -> client.initiateDownloads(u, toRetry), virtualThreadExecutor)
                                    .orTimeout(30000, TimeUnit.MILLISECONDS);
                        });
            }
        };
        if (runImmediately) {
            retryDownloads.run();
        }
        downloadExecutor.scheduleAtFixedRate(retryDownloads, 300000, 300000, TimeUnit.MILLISECONDS);
        return this;
    }

    private int pollCount = 0;

    @CanIgnoreReturnValue
    public SlskdService startDownloadPoll() {
        if (startedDownloadPoll) {
            return this;
        }
        startedDownloadPoll = true;

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

            if (pollCount++ % 10 != 0) {
                return;
            }
            Set<DownloadSubscription> allSubs = subscriptions.values().stream().flatMap(Collection::stream).collect(Collectors.toSet());
            downloadsList.forEach(gdr -> {
                var subs = subscriptions.get(gdr.username());
                if (subs == null || subs.isEmpty()) {
                    return;
                }
                // Remove subs that are getting called with actual args
                allSubs.removeAll(subs);
                Map<DownloadSubscription, List<SlskdDownloadFileResponse>> subArgs = subs.stream()
                        .collect(Collectors.toMap(
                                Function.identity(),
                                f -> new ArrayList<>()
                        ));
                gdr.directories().stream()
                        .flatMap(d -> d.files().stream())
                        .forEach(dfr -> {
                            boolean found = false;
                            var it = subs.iterator();
                            while (!found && it.hasNext()) {
                                var sub = it.next();
                                if (sub.filenames().contains(dfr.filename())) {
                                    subArgs.get(sub).add(dfr);
                                    found = true;
                                }
                            }
                        });
                subArgs.forEach((sub, args) -> CompletableFuture.runAsync(() -> sub.callback().accept(args, gdr), virtualThreadExecutor));
            });
            // Call the remaining ones with empty params
            allSubs.forEach((sub) -> CompletableFuture.runAsync(() -> sub.callback().accept(List.of(),
                    new SlskdGetDownloadResponse("unknown", List.of())), virtualThreadExecutor));
        };
        // Run it once inline, blocking the calling thread, to initialise 'can download' properly
        checkCanDownload.run();
        // Run all other invocations on the executor
        downloadExecutor.scheduleAtFixedRate(checkCanDownload, 1000, 1000, TimeUnit.MILLISECONDS);
        return this;
    }

    /**
     * Allows starting the service separately to instantiation
     * @return this object (for chaining)
     */
    public SlskdService start() {

        startDownloadPoll();
//        startRetrier(false);
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

    public void initiateAndSubscribe(final String hostUser, final List<SlskdDownloadRequest> files, BiConsumer<List<SlskdDownloadFileResponse>, SlskdGetDownloadResponse> onUpdate) {
        // Wait for the download to be initiated before we subscribe, because initiateDownloads might block
        initiateDownloads(hostUser, files);
        subscriptions.computeIfAbsent(hostUser, k -> ConcurrentHashMap.newKeySet())
                .add(new DownloadSubscription(files.stream().map(SlskdDownloadRequest::filename).collect(Collectors.toSet()), onUpdate));
    }

    public void unsubscribe(final String hostUser, final List<SlskdDownloadRequest> files, BiConsumer<List<SlskdDownloadFileResponse>, SlskdGetDownloadResponse> onUpdate) {

        subscriptions.computeIfAbsent(hostUser, k -> ConcurrentHashMap.newKeySet())
                .remove(new DownloadSubscription(files.stream().map(SlskdDownloadRequest::filename).collect(Collectors.toSet()), onUpdate));
    }

    public void cancelDownloads(final String hostUser, final List<UUID> fileIds, boolean remove) {

        fileIds.forEach(id -> client.cancelDownload(hostUser, id, remove));
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

    private record DownloadSubscription(Set<String> filenames, BiConsumer<List<SlskdDownloadFileResponse>, SlskdGetDownloadResponse> callback) { }
}
