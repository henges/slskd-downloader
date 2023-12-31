package dev.polluxus.slskd_downloader.processor.model.output;

import dev.polluxus.slskd_downloader.config.Builder;
import dev.polluxus.slskd_downloader.processor.model.output.ProcessorFileResultBuilder;

import java.util.List;
import java.util.Map;

@Builder
public record ProcessorUserResult(
        String username,
        List<ProcessorFileResult> bestCandidates,
        int uploadSpeed,
        double scoreOfBestCandidates,
        List<ProcessorDirectoryResultBuilder> directories
) {
}
