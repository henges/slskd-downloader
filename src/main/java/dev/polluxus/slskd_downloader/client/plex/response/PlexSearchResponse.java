package dev.polluxus.slskd_downloader.client.plex.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record PlexSearchResponse(
        @JsonProperty("MediaContainer") PlexSearchMediaContainer mediaContainer
) {

    public record PlexSearchMediaContainer(
            int size,
            @JsonProperty("Hub") List<PlexSearchHubElement> hub
    ) {}

    public record PlexSearchHubElement(
            String type,
            @JsonProperty("Metadata") List<PlexSearchMetadataElement> metadata
    ) {}

    public record PlexSearchMetadataElement(
            String title,
            @JsonProperty("parentTitle") String artistName
    ) {}
}
