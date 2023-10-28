package dev.polluxus.slskd_downloader.service;

import dev.polluxus.slskd_downloader.client.plex.PlexClient;
import dev.polluxus.slskd_downloader.client.plex.response.PlexSearchResponse;
import dev.polluxus.slskd_downloader.model.AlbumInfo;
import dev.polluxus.slskd_downloader.util.Matchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeduplicatorService {

    private static final Logger log = LoggerFactory.getLogger(DeduplicatorService.class);

    private final PlexClient plexClient;

    public DeduplicatorService(PlexClient plexClient) {
        this.plexClient = plexClient;
    }

    public boolean shouldDownload(AlbumInfo albumInfo) {

        final PlexSearchResponse resp;
        try {
            // Search for the album name
            resp = plexClient.searchAlbums(albumInfo.name());
        } catch (Exception e) {
            log.error("Error searching Plex for string {}", albumInfo.name(), e);
            return true;
        }
        final var matches = resp.mediaContainer().hub()
                .stream()
                .filter(e -> e.type().equals("album"))
                .filter(e -> e.metadata() != null)
                .flatMap(e -> e.metadata().stream())
                // Try to match any of the artists to it
                .filter(md -> albumInfo.artists().stream()
                        .anyMatch(e -> Matchers.getEditDistanceFunc(e).apply(e, md.artistName()) != -1))
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
