package dev.polluxus.slskd_downloader.service;

import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import dev.polluxus.slskd_downloader.config.Config;
import dev.polluxus.slskd_downloader.client.slskd.SlskdClient;
import dev.polluxus.slskd_downloader.client.slskd.request.SlskdDownloadRequest;
import dev.polluxus.slskd_downloader.client.slskd.response.SlskdSearchDetailResponse;
import dev.polluxus.slskd_downloader.client.slskd.response.SlskdSearchStateResponse;
import dev.polluxus.slskd_downloader.model.AlbumInfo;
import dev.polluxus.slskd_downloader.util.FutureUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SlskdService {

    private static final Logger log = LoggerFactory.getLogger(SlskdService.class);

    private final SlskdClient client;
    private final ExecutorService pool;
    private final Map<String, SlskdSearchStateResponse> allSearchStates;

    public SlskdService(Config config) {
        this.client = SlskdClient.create(config);
        this.pool = Executors.newWorkStealingPool();
        this.allSearchStates = client.getAllSearchStates()
                .stream()
                .collect(Collectors.toMap(
                        SlskdSearchStateResponse::searchText,
                        Function.identity(),
                        // Searches are returned earliest -> latest, so use the freshest
                        // result only
                        (o1, o2) -> o2
                ));
    }

    public CompletableFuture<List<SlskdSearchDetailResponse>> search(final AlbumInfo albumInfo) {

        return search(albumInfo.searchString());
    }

    public CompletableFuture<List<SlskdSearchDetailResponse>> search(final String searchString) {

        return CompletableFuture.supplyAsync(() -> {
            final SlskdSearchStateResponse initResp;
            if (allSearchStates.containsKey(searchString)) {
                log.info("Found existing search for string {}", searchString);
                initResp = allSearchStates.get(searchString);
            } else {
                log.info("Creating new search for {}", searchString);
                initResp = client.search(searchString);
            }

            String state = initResp.state();
            while (!state.contains("Completed")) {
                final var currState = client.getSearchState(initResp.id());
                state = currState.state();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // Ignore
                }
            }

            var responses = client.getSearchResponses(initResp.id());

            log.info("Got {} responses for query {}", responses.size(), searchString);

            return responses;
        }, this.pool)
        .exceptionally(t -> {
            log.error("Exception while retrieving search for {}", searchString, t);
            return List.of();
        });
    }

    public boolean initiateDownloads(final String hostUser, final List<SlskdDownloadRequest> files) {

        try {
            client.initiateDownloads(hostUser, files);
            return true;
        } catch (Exception e) {
            log.error("Error starting download request from user {} for {}", hostUser, files, e);
            return false;
        }
    }

    public void shutdown() {
        this.pool.shutdownNow();
        try {
            this.pool.awaitTermination(15000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            log.error("Error terminating SlskdService worker pool", e);
        }
    }
}
