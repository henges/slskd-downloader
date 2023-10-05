package dev.polluxus.spotify_offline_playlist.service;

import dev.polluxus.spotify_offline_playlist.Config;
import dev.polluxus.spotify_offline_playlist.client.slskd.SlskdClient;
import dev.polluxus.spotify_offline_playlist.client.slskd.response.SlskdSearchDetailResponse;
import dev.polluxus.spotify_offline_playlist.client.slskd.response.SlskdSearchStateResponse;
import dev.polluxus.spotify_offline_playlist.model.AlbumInfo;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SlskdService {

    private final SlskdClient client;
    private final ExecutorService pool;
    private final ScheduledExecutorService scheduler;

    private final Object searchStatesMu = new Object();
    private Map<String, SlskdSearchStateResponse> allSearchStates;


    public SlskdService(Config config) {
        this.client = SlskdClient.create(config);
        this.pool = Executors.newWorkStealingPool();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        // Repeatedly poll for current searches to avoid bombarding
        scheduler.scheduleAtFixedRate(() -> {
            synchronized (searchStatesMu) {
                allSearchStates = client.getAllSearchStates()
                        .stream()
                        .collect(Collectors.toMap(
                                SlskdSearchStateResponse::searchText,
                                Function.identity()
                        ));
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
}
