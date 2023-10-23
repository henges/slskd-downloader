package dev.polluxus.slskd_downloader.client.slskd;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import dev.polluxus.slskd_downloader.client.slskd.response.SlskdGetDownloadResponse;
import dev.polluxus.slskd_downloader.config.Config;
import dev.polluxus.slskd_downloader.client.AbstractHttpClient;
import dev.polluxus.slskd_downloader.client.slskd.request.SlskdDownloadRequest;
import dev.polluxus.slskd_downloader.client.slskd.request.SlskdLoginRequest;
import dev.polluxus.slskd_downloader.client.slskd.request.SlskdSearchRequest;
import dev.polluxus.slskd_downloader.client.slskd.response.SlskdLoginResponse;
import dev.polluxus.slskd_downloader.client.slskd.response.SlskdSearchDetailResponse;
import dev.polluxus.slskd_downloader.client.slskd.response.SlskdSearchStateResponse;
import dev.polluxus.slskd_downloader.config.JacksonConfig;
import dev.polluxus.slskd_downloader.config.UoeDefaultConfig;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

public class SlskdClient extends AbstractHttpClient {

    private static final Logger log = LoggerFactory.getLogger(SlskdClient.class);

    private static final String API_PREFIX = "/api/v0";

    private final String baseUrl;
    private final String username;
    private final String password;

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

    public static void main(String[] args) {
        final var c = create(new UoeDefaultConfig() {
            @Override
            public String slskdBaseUrl() {
                return "http://burt-mediaserv:5030";
            }

            @Override
            public String slskdUsername() {
                return "slskd";
            }

            @Override
            public String slskdPassword() {
                return "slskd";
            }
        });
        log.info("Client acuiqred");
        final List<SlskdSearchStateResponse> states = c.getAllSearchStates();
        final List<SlskdSearchStateResponse> reversed = Lists.reverse(states);
        final List<SlskdSearchStateResponse> mostRecent10 = reversed.stream().filter(s -> !"coil musick".equals(s.searchText())).limit(10).toList();
        final var mostRecent10Details = mostRecent10.stream()
                .map(r -> c.getSearchResponses(r.id()))
                .toList();

        log.info("Stop");
    }

    private SlskdClient(String baseUrl, String username, String password, HttpClient client, ObjectMapper mapper) {
        super(client, mapper);
        this.baseUrl = baseUrl;
        this.username = username;
        this.password = password;
    }

    private void ensureAuthValid() {
        if (token == null || token.expires <= System.currentTimeMillis() / 1000) {
            final SlskdLoginResponse loginResponse = login();
            this.token = new SlskdToken(loginResponse.expires(), loginResponse.token(), loginResponse.tokenType());
        }
    }

    public static SlskdClient create(Config config) {

        final HttpClient client = HttpClientBuilder.create().build();
        final ObjectMapper mapper = JacksonConfig.MAPPER
                ;
        final SlskdClient ret = new SlskdClient(
                config.slskdBaseUrl(), config.slskdUsername(), config.slskdPassword(), client, mapper);
        ret.ensureAuthValid();
        return ret;
    }

    public List<SlskdSearchStateResponse> getAllSearchStates() {

        ensureAuthValid();
        final var req = ClassicRequestBuilder.get(baseUrl + API_PREFIX + "/searches")
                .addHeader("Authorization", token.headerValue())
                .build();
        return doRequest(req, new TypeReference<>() {});
    }

    public SlskdSearchStateResponse getSearchState(UUID id) {

        ensureAuthValid();
        final var req = ClassicRequestBuilder.get(baseUrl + API_PREFIX + "/searches/" + id)
                .addHeader("Authorization", token.headerValue())
                .build();
        return doRequest(req, SlskdSearchStateResponse.class);
    }

    public List<SlskdSearchDetailResponse> getSearchResponses(UUID id) {

        ensureAuthValid();
        final var req = ClassicRequestBuilder.get(baseUrl + API_PREFIX + "/searches/" + id + "/responses")
                .addHeader("Authorization", token.headerValue())
                .build();
        return doRequest(req, new TypeReference<>() {});
    }

    public List<SlskdGetDownloadResponse> getAllDownloads() {

        ensureAuthValid();
        final var req = ClassicRequestBuilder.get(baseUrl + API_PREFIX + "/transfers/downloads")
                .addHeader("Authorization", token.headerValue())
                .build();
        return doRequest(req, new TypeReference<>() {});
    }

    public SlskdSearchStateResponse search(String searchText) {

        ensureAuthValid();
        final var req = ClassicRequestBuilder.post(baseUrl + API_PREFIX + "/searches")
                .addHeader("Authorization", token.headerValue())
                .setEntity(writeValueAsBytesUnchecked(new SlskdSearchRequest(searchText)), ContentType.APPLICATION_JSON)
                .build();
        return doRequest(req, SlskdSearchStateResponse.class);
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
}
