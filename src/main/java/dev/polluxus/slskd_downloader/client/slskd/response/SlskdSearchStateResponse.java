package dev.polluxus.slskd_downloader.client.slskd.response;

import java.util.List;
import java.util.UUID;

public record SlskdSearchStateResponse(
        String endedAt,
        int fileCount,
        UUID id,
        boolean isComplete,
        int lockedFileCount,
        int responseCount,
        // This is always empty
        List<String> responses,
        String searchText,
        String startedAt,
        String state,
        int token
) {
}
