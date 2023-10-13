package dev.polluxus.spotify_offline_playlist.processor.model;

import io.soabase.recordbuilder.core.RecordBuilder;

import java.util.List;

@RecordBuilder
public record ProcessorUserResult(
        String username,
        List<ProcessorFileResult> files
) {
}
