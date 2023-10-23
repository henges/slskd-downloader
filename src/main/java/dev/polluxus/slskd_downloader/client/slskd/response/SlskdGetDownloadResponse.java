package dev.polluxus.slskd_downloader.client.slskd.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record SlskdGetDownloadResponse(
        String username,
        List<SlskdDownloadDirectoryResponse> directories
) {

    public record SlskdDownloadDirectoryResponse(
            String directory,
            int fileCount,
            List<SlskdDownloadFileResponse> files
    ) {}

    public record SlskdDownloadFileResponse(
            UUID id,
            String username,
            String direction,
            String filename,
            long size,
            int startOffset,
            String state,
            LocalDateTime requestedAt,
            LocalDateTime enqueuedAt,
            long bytesTransferred,
            double averageSpeed,
            Optional<Integer> placeInQueue,
            long bytesRemaining,
            double percentComplete
    ){}
}
