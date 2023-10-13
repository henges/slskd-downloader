package dev.polluxus.spotify_offline_playlist.processor.model;

import dev.polluxus.spotify_offline_playlist.client.slskd.response.SlskdSearchDetailResponse.SlskdSearchMatchResponse;
import dev.polluxus.spotify_offline_playlist.config.Builder;
import io.soabase.recordbuilder.core.RecordBuilder;

import javax.annotation.Nullable;

@Builder
public record ProcessorFileResult(
        double score,
        SlskdSearchMatchResponse originalData,
        ProcessorMatchDetails matchDetails,
        boolean isTargetFormat,
        boolean sizeOk
) {

    @Builder
    public record ProcessorMatchDetails(
            String matchesTitle,
            // Only for Levenshtein
            @Nullable Integer distance
    ) {}
}
