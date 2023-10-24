package dev.polluxus.slskd_downloader.processor.model.output;

import dev.polluxus.slskd_downloader.processor.model.output.ProcessorFileResultBuilder;

import java.util.List;
import java.util.Map;

public record ProcessorDirectoryResult(
        Map<String, List<ProcessorFileResultBuilder>> byTrackName
) {
}
