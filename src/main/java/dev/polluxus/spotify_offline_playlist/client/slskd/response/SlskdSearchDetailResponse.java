package dev.polluxus.spotify_offline_playlist.client.slskd.response;

import java.util.List;
import java.util.Optional;

public record SlskdSearchDetailResponse(
        int fileCount,
        boolean hasFreeUploadSlot,
        int lockedFileCount,
        // We don't care about these.
        List<Object> lockedFiles,
        int queueLength,
        int token,
        int uploadSpeed,
        String username,
        List<SlskdSearchMatchResponse> files
) {

    public record SlskdSearchMatchResponse(
            int code,
            String extension,
            String filename,
            long size,
            boolean isLocked,
            // Optional
            Optional<Integer> bitDepth,
            // Optional
            Optional<Integer> bitRate,
            // Optional, length in seconds
            Optional<Integer> length
    ) {}
    
}
