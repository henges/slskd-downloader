package dev.polluxus.spotify_offline_playlist.processor.model;

import dev.polluxus.spotify_offline_playlist.config.Builder;
import io.soabase.recordbuilder.core.RecordBuilder;

import java.util.List;
import java.util.Map;

@Builder
public record ProcessorUserResult(
        String username,
        List<ProcessorFileResult> bestCandidates,
        double scoreOfBestCandidates,
        Map<String, List<ProcessorFileResultBuilder>> byTrackName
) {
}
