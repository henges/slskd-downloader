package dev.polluxus.spotify_offline_playlist.client.slskd;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import dev.polluxus.spotify_offline_playlist.Config;
import dev.polluxus.spotify_offline_playlist.client.musicbrainz.dto.MusicbrainzReleaseSearchResult;
import dev.polluxus.spotify_offline_playlist.client.slskd.request.SlskdDownloadRequest;
import dev.polluxus.spotify_offline_playlist.client.slskd.request.SlskdLoginRequest;
import dev.polluxus.spotify_offline_playlist.client.slskd.request.SlskdSearchRequest;
import dev.polluxus.spotify_offline_playlist.client.slskd.response.SlskdLoginResponse;
import dev.polluxus.spotify_offline_playlist.client.slskd.response.SlskdSearchDetailResponse;
import dev.polluxus.spotify_offline_playlist.client.slskd.response.SlskdSearchEntryResponse;
import dev.polluxus.spotify_offline_playlist.store.FileBackedStore;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

public class SlskdClient {

    private static final Logger log = LoggerFactory.getLogger(SlskdClient.class);

    private static final String API_PREFIX = "/api/v0";

    private final String baseUrl;
    private final String username;
    private final String password;
    private final HttpClient client;
    private final ObjectMapper mapper;

    private SlskdToken token;

    private record SlskdToken(
            // Token expiry time in Unix seconds
            int expires,
            // The token value
            String token,
            // The token type, i.e. Bearer
            String tokenType
    ) {
        String headerValue() {
            return tokenType + " " + token;
        }
    }

    public SlskdClient(String baseUrl, String username, String password, HttpClient client, ObjectMapper mapper) {
        this.baseUrl = baseUrl;
        this.username = username;
        this.password = password;
        this.client = client;
        this.mapper = mapper;
    }

    private void ensureAuthValid() {
        if (token == null || token.expires <= System.currentTimeMillis() / 1000) {
            final SlskdLoginResponse loginResponse = login();
            this.token = new SlskdToken(loginResponse.expires(), loginResponse.token(), loginResponse.tokenType());
        }
    }

    public static SlskdClient create(Config config) {

        final HttpClient client = HttpClientBuilder.create().build();
        final ObjectMapper mapper = new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .registerModule(new Jdk8Module())
                ;
        return new SlskdClient(
                config.slskdBaseUrl(), config.slskdUsername(), config.slskdPassword(), client, mapper);
    }

    public List<SlskdSearchEntryResponse> getSearches() {

        ensureAuthValid();
        final var req = ClassicRequestBuilder.get(baseUrl + API_PREFIX + "/searches")
                .addHeader("Authorization", token.headerValue())
                .build();
        return doRequest(req, new TypeReference<>() {});
    }

    public List<SlskdSearchDetailResponse> getSearch(UUID id) {

        ensureAuthValid();
        final var req = ClassicRequestBuilder.get(baseUrl + API_PREFIX + "/searches/" + id + "/responses")
                .addHeader("Authorization", token.headerValue())
                .build();
        return doRequest(req, new TypeReference<>() {});
    }

    public SlskdSearchEntryResponse search(String searchText) {

        ensureAuthValid();
        final var req = ClassicRequestBuilder.post(baseUrl + API_PREFIX + "/searches")
                .addHeader("Authorization", token.headerValue())
                .setEntity(writeValueAsBytesUnchecked(new SlskdSearchRequest(searchText)), ContentType.APPLICATION_JSON)
                .build();
        return doRequest(req, SlskdSearchEntryResponse.class);
    }

    public void initiateDownloads(final String hostUser, final List<SlskdDownloadRequest> files) {

        ensureAuthValid();
        final var req = ClassicRequestBuilder.post(baseUrl + API_PREFIX + "/transfers/downloads/" + hostUser)
                .addHeader("Authorization", token.headerValue())
                .setEntity(writeValueAsBytesUnchecked(files), ContentType.APPLICATION_JSON)
                .build();
        doRequest(req, 201, Void.class);
    }

    private SlskdLoginResponse login() {

        final var req = ClassicRequestBuilder.post(baseUrl + API_PREFIX + "/session")
                .setEntity(writeValueAsBytesUnchecked(new SlskdLoginRequest(username, password)), ContentType.APPLICATION_JSON)
                .build();
        return doRequest(req, SlskdLoginResponse.class);
    }

    private <T> T doRequest(final ClassicHttpRequest req, final Class<T> klazz) {

        return doRequest(req, 200, klazz);
    }


    private <T> T doRequest(final ClassicHttpRequest req, final TypeReference<T> typeReference) {

        return doRequest(req, 200, typeReference);
    }

    private <T> T doRequest(final ClassicHttpRequest req, final int expectedStatus, final Class<T> klazz) {

        return executeUnchecked(req, resp -> {
            if (resp.getCode() != expectedStatus) {
                throw new RuntimeException("Expected status code " + expectedStatus + ", but got " + resp.getCode());
            }
            if (klazz == Void.class) {
                return null;
            }
            return readValueUnchecked(resp.getEntity().getContent(), klazz);
        });
    }

    private <T> T doRequest(final ClassicHttpRequest req, final int expectedStatus, final TypeReference<T> typeReference) {

        return executeUnchecked(req, resp -> {
            if (resp.getCode() != expectedStatus) {
                throw new RuntimeException("Expected status code " + expectedStatus + ", but got " + resp.getCode());
            }
            return readValueUnchecked(resp.getEntity().getContent(), typeReference);
        });
    }

    private <T> T executeUnchecked(ClassicHttpRequest req, HttpClientResponseHandler<T> handler) {

        try {
            return client.execute(req, handler);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private <T> T readValueUnchecked(final InputStream in, Class<T> klazz) {

        try {
            return mapper.readValue(in, klazz);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private <T> T readValueUnchecked(final InputStream in, TypeReference<T> klazz) {

        try {
            return mapper.readValue(in, klazz);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] writeValueAsBytesUnchecked(final Object value) {

        try {
            return mapper.writeValueAsBytes(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
