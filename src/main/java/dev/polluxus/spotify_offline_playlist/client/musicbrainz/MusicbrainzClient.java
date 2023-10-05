package dev.polluxus.spotify_offline_playlist.client.musicbrainz;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.polluxus.spotify_offline_playlist.Config;
import dev.polluxus.spotify_offline_playlist.client.musicbrainz.dto.MusicbrainzReleaseSearchResult;
import dev.polluxus.spotify_offline_playlist.store.FileBackedStore;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.http.support.BasicRequestBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class MusicbrainzClient {

    private static final Logger log = LoggerFactory.getLogger(MusicbrainzClient.class);
    private static final String BASE_URL = "https://musicbrainz.org/ws/2";
    private static final String QUERY_STRATEGY = "BROAD_SEARCH";

    private static final long LOCK_DURATION = 1100L;

    private final HttpClient client;
    private final FileBackedStore<MusicbrainzReleaseSearchResult> store;
    private final ObjectMapper mapper;

    private long lockedUntil;

    MusicbrainzClient(HttpClient client, FileBackedStore<MusicbrainzReleaseSearchResult> store, ObjectMapper mapper) {
        this.client = client;
        this.store = store;
        this.mapper = mapper;
    }

    public static MusicbrainzClient create(Config config) {
        final HttpClient client = HttpClientBuilder.create().build();
        final FileBackedStore<MusicbrainzReleaseSearchResult> store = FileBackedStore.from(config, MusicbrainzReleaseSearchResult.class);
        final ObjectMapper mapper = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return new MusicbrainzClient(client, store, mapper);
    }

    public MusicbrainzReleaseSearchResult searchReleases(final String artist, final String album) {

        final String query = URLEncoder.encode(buildQuery(album, artist), StandardCharsets.UTF_8);
        final MusicbrainzReleaseSearchResult memo = store.get(query.replace("*", ""));
        if (memo != null) {
            return memo;
        }

        final ClassicHttpRequest req = ClassicRequestBuilder
                .get(BASE_URL + "/release" + "?query=" + query + "&fmt=json")
                .build();
        final MusicbrainzReleaseSearchResult result = executeUnchecked(req, resp ->
                mapper.readValue(resp.getEntity().getContent(), MusicbrainzReleaseSearchResult.class));
        store.put(query.replace("*", ""), result);
        return result;
    }

    private String buildQuery(String album, String artist) {
        switch (QUERY_STRATEGY) {
            case "FIELD_SEARCH": return "\"" + album + "\" AND artist:\"" + artist + "\"";
            case "BROAD_SEARCH":
            default:
                return album + " " + artist;
        }
    }

    private void acquireLock() {

        final long now = System.currentTimeMillis();
        if (lockedUntil > now) {

            log.info("Musicbrainz locked until {}, waiting {} millis before continuing", lockedUntil, lockedUntil - now);

            try {
                Thread.sleep(lockedUntil - now);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        lockedUntil = System.currentTimeMillis() + LOCK_DURATION;
    }

    private <T> T executeUnchecked(ClassicHttpRequest req, HttpClientResponseHandler<T> handler) {

        try {
            acquireLock();
            log.info("{}", req.getRequestUri());
            return client.execute(req, handler);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
