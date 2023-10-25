package dev.polluxus.slskd_downloader.processor.model.output;

import dev.polluxus.slskd_downloader.processor.model.output.ProcessorFileResultBuilder;
import io.soabase.recordbuilder.core.RecordBuilder;

import java.util.List;
import java.util.Map;

@RecordBuilder
public record ProcessorDirectoryResult(
        Map<String, List<ProcessorFileResultBuilder>> byTrackName,
        List<ProcessorFileResult> bestCandidates,
        double score
) {
}
