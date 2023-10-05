package dev.polluxus.spotify_offline_playlist.service;

import dev.polluxus.spotify_offline_playlist.Config;
import dev.polluxus.spotify_offline_playlist.client.slskd.SlskdClient;
import dev.polluxus.spotify_offline_playlist.client.slskd.request.SlskdDownloadRequest;
import dev.polluxus.spotify_offline_playlist.client.slskd.response.SlskdSearchDetailResponse;
import dev.polluxus.spotify_offline_playlist.client.slskd.response.SlskdSearchStateResponse;
import dev.polluxus.spotify_offline_playlist.model.AlbumInfo;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
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
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        // Repeatedly poll for current searches to avoid bombarding
        scheduler.scheduleAtFixedRate(() -> {
            synchronized (searchStatesMu) {
                log.info("Running scheudled search state update");
                allSearchStates = client.getAllSearchStates()
                        .stream()
                        .collect(Collectors.toMap(
                                SlskdSearchStateResponse::searchText,
                                Function.identity()
                        ));
                log.info("Scheduled search state update complete");
            }
        }, 0, 15, TimeUnit.SECONDS);
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
                } else {
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

            return client.getSearchResponses(initResp.id());
            }, this.pool)
            .exceptionally(t -> List.of());
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
