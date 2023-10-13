package dev.polluxus.slskd_downloader.processor.model;

import dev.polluxus.slskd_downloader.client.slskd.response.SlskdSearchDetailResponse.SlskdSearchMatchResponse;
import dev.polluxus.slskd_downloader.config.Builder;

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
