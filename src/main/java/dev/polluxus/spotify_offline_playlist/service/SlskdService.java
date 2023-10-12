package dev.polluxus.spotify_offline_playlist.service;

import dev.polluxus.spotify_offline_playlist.Config;
import dev.polluxus.spotify_offline_playlist.client.slskd.SlskdClient;
import dev.polluxus.spotify_offline_playlist.client.slskd.request.SlskdDownloadRequest;
import dev.polluxus.spotify_offline_playlist.client.slskd.response.SlskdSearchDetailResponse;
import dev.polluxus.spotify_offline_playlist.client.slskd.response.SlskdSearchStateResponse;
import dev.polluxus.spotify_offline_playlist.model.AlbumInfo;
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
    private final ScheduledExecutorService scheduler;

    private static final Object searchStatesMu = new Object();
    private Map<String, SlskdSearchStateResponse> allSearchStates;


    public SlskdService(Config config) {
        this.client = SlskdClient.create(config);
        this.pool = Executors.newWorkStealingPool();
        this.scheduler = Executors.newScheduledThreadPool(1);
        // Repeatedly poll for current searches to avoid bombarding
        Runnable run = () -> {
            synchronized (searchStatesMu) {
                log.info("Running scheudled search state update");
                try {
                    allSearchStates = client.getAllSearchStates()
                            .stream()
                            .filter(r -> r.responses().size() > 0)
                            .collect(Collectors.toMap(
                                    SlskdSearchStateResponse::searchText,
                                    Function.identity(),
                                    (e1, e2) -> e1
                            ));
                } catch (Exception e) {
                    log.error("Error in scheduled refresh", e);
                }
                log.info("Scheduled search state update complete");
            }
        };
        run.run();
        scheduler.scheduleAtFixedRate(run, 15, 15, TimeUnit.SECONDS);
    }

    public CompletableFuture<List<SlskdSearchDetailResponse>> search(final AlbumInfo albumInfo) {

        return search(albumInfo.name() + " " + String.join(" ", albumInfo.artists()));
    }

    public CompletableFuture<List<SlskdSearchDetailResponse>> search(final String searchString) {

        return CompletableFuture.supplyAsync(() -> {
            final SlskdSearchStateResponse initResp;

            synchronized (searchStatesMu) {
                if (allSearchStates.containsKey(searchString)) {
                    initResp = allSearchStates.get(searchString);
                    log.info("Found existing response for {}", searchString);
                } else {
                    log.info("Creating new search for {}", searchString);
                    initResp = client.search(searchString);
                }
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
}
