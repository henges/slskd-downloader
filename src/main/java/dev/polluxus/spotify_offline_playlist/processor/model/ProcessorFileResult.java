package dev.polluxus.spotify_offline_playlist.processor.model;

import io.soabase.recordbuilder.core.RecordBuilder;

import javax.annotation.Nullable;

@RecordBuilder
public record ProcessorFileResult(
        String filename,
        ProcessorMatchDetails matchDetails,
        boolean isTargetFormat,
        boolean sizeOk
) {

    @RecordBuilder
    public record ProcessorMatchDetails(
            String matchesTitle,
            // Only for Levenshtein
            @Nullable Integer distance
    ) {}
}
