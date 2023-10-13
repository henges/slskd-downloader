package dev.polluxus.spotify_offline_playlist.processor.matcher;

import dev.polluxus.spotify_offline_playlist.client.slskd.response.SlskdSearchDetailResponse;
import dev.polluxus.spotify_offline_playlist.model.AlbumInfo;
import dev.polluxus.spotify_offline_playlist.processor.model.ProcessorFileResult;
import dev.polluxus.spotify_offline_playlist.processor.model.ProcessorFileResultBuilder;

import java.util.List;
import java.util.Map;

public enum MatchStrategyType {
    PATTERN_MATCH(new PatternMatchStrategy()),
    EDIT_DISTANCE(new EditDistanceStrategy());

    private final MatchStrategy func;

    MatchStrategyType(MatchStrategy func) {
        this.func = func;
    }

    public Map<String, List<ProcessorFileResultBuilder>> match(SlskdSearchDetailResponse resp, AlbumInfo albumInfo) {
        return this.func.apply(resp, albumInfo);
    }
}
