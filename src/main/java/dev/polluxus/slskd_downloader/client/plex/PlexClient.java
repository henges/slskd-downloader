package dev.polluxus.slskd_downloader.client.plex;

import dev.polluxus.slskd_downloader.client.AbstractHttpClient;
import dev.polluxus.slskd_downloader.client.plex.response.PlexSearchResponse;
import dev.polluxus.slskd_downloader.config.Config;
import dev.polluxus.slskd_downloader.config.JacksonConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class PlexClient extends AbstractHttpClient {

    private static final Logger log = LoggerFactory.getLogger(PlexClient.class);

    private final String baseUrl;
    private final String plexToken;
    private final String librarySectionId;

    public PlexClient(String baseUrl, String plexToken, String librarySectionId) {
        super(HttpClients.createDefault(), JacksonConfig.MAPPER);
        this.baseUrl = baseUrl;
        this.plexToken = plexToken;
        this.librarySectionId = librarySectionId;
    }

    public static PlexClient create(Config config) {

        return new PlexClient(config.plexBaseUrl().orElseThrow(),
                config.plexToken().orElseThrow(),
                config.plexLibrarySectionId().orElseThrow());
    }

    public PlexSearchResponse searchAlbums(String query) {

        log.debug("Seeing if should download {}", query);

        final var req = ClassicRequestBuilder.get(
                STR."\{baseUrl}/hubs/search?type=9&sectionId=\{librarySectionId}&query=\{URLEncoder.encode(query, StandardCharsets.UTF_8)}")
                .addHeader("X-Plex-Token", plexToken)
                .addHeader("Accept", "application/json")
                .build();
        return doRequest(req, PlexSearchResponse.class);
    }
}
