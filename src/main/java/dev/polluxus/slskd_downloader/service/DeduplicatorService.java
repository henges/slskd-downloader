package dev.polluxus.slskd_downloader.service;

import dev.polluxus.slskd_downloader.client.plex.PlexClient;
import dev.polluxus.slskd_downloader.client.plex.response.PlexSearchResponse;
import dev.polluxus.slskd_downloader.model.AlbumInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeduplicatorService {

    private static final Logger log = LoggerFactory.getLogger(DeduplicatorService.class);

    private final PlexClient plexClient;

    public DeduplicatorService(PlexClient plexClient) {
        this.plexClient = plexClient;
    }

    public boolean shouldDownload(AlbumInfo albumInfo) {

        PlexSearchResponse resp = plexClient.searchAlbums(albumInfo.searchString());
        final var matches = resp.mediaContainer().hub()
                .stream()
                .filter(e -> e.type().equals("album"))
                .flatMap(e -> e.metadata().stream())
                .toList();
        if (matches.isEmpty()) {
            log.debug("No preexisting library item found for {}", albumInfo.searchString());
            return true;
        }

        log.debug("Found existing library item {} for {}", STR."\{matches.getFirst().artistName()} \{matches.getFirst().title()}",
                albumInfo.searchString());
        return false;
    }
}
