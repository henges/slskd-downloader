package dev.polluxus.slskd_downloader.client.musicbrainz;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.polluxus.slskd_downloader.client.AbstractRateLimitedHttpClient;
import dev.polluxus.slskd_downloader.client.musicbrainz.dto.MusicbrainzRecording;
import dev.polluxus.slskd_downloader.client.musicbrainz.dto.MusicbrainzReleaseSearchResult.MusicbrainzRelease;
import dev.polluxus.slskd_downloader.config.Config;
import dev.polluxus.slskd_downloader.client.musicbrainz.dto.MusicbrainzReleaseSearchResult;
import dev.polluxus.slskd_downloader.config.JacksonConfig;
import dev.polluxus.slskd_downloader.config.UoeDefaultConfig;
import dev.polluxus.slskd_downloader.store.FileBackedStore;
import dev.polluxus.slskd_downloader.store.Store;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.UUID;
import java.util.function.Function;

public class MusicbrainzClient extends AbstractRateLimitedHttpClient {

    private static final Logger log = LoggerFactory.getLogger(MusicbrainzClient.class);
    private static final String BASE_URL = "https://musicbrainz.org/ws/2";
    private static final String QUERY_STRATEGY = "FIELD_SEARCH";

    private final Store<MusicbrainzReleaseSearchResult> searchStore;
    private final Store<MusicbrainzRecording> mbRecordingStore;

    MusicbrainzClient(HttpClient client,
                      Store<MusicbrainzReleaseSearchResult> searchStore,
                      Store<MusicbrainzRecording> mbRecordingStore,
                      ObjectMapper mapper) {
        super(client, mapper);
        this.searchStore = searchStore;
        this.mbRecordingStore = mbRecordingStore;
    }

    public static void main(String[] args) {

        Config config = new UoeDefaultConfig() {
            @Override
            public String dataDirectory() {
                return "C:\\Users\\alexa\\.spotify_offline_playlist";
            }
        };

        var c = MusicbrainzClient.create(config);
        System.out.println("Client acquired.");
    }

    public static MusicbrainzClient create(Config config) {
        final HttpClient client = HttpClientBuilder.create().build();
        final FileBackedStore<MusicbrainzReleaseSearchResult> searchStore = FileBackedStore.from(config, MusicbrainzReleaseSearchResult.class);
        final FileBackedStore<MusicbrainzRecording> mbRecordingStore = FileBackedStore.from(config, MusicbrainzRecording.class);
        final ObjectMapper mapper = JacksonConfig.MAPPER;
        return new MusicbrainzClient(client, searchStore, mbRecordingStore, mapper);
    }

    public enum SearchOptions {
        SORTED_DATE((r) -> {
            final var sorted = r.releases().stream()
                    .filter(e -> e.date() != null)
                    .sorted(Comparator.comparing(MusicbrainzRelease::date)).toList();
            return new MusicbrainzReleaseSearchResult(r.created(), r.count(), r.offset(), sorted);
        });

        private final Function<MusicbrainzReleaseSearchResult, MusicbrainzReleaseSearchResult> f;

        SearchOptions(Function<MusicbrainzReleaseSearchResult, MusicbrainzReleaseSearchResult> f) {
            this.f = f;
        }

        public MusicbrainzReleaseSearchResult apply(MusicbrainzReleaseSearchResult in) {
            return f.apply(in);
        }

        public static MusicbrainzReleaseSearchResult applyMany(MusicbrainzReleaseSearchResult in, SearchOptions... opts) {

            var i = in;
            for (var opt : opts) {
                i = opt.apply(i);
            }
            return i;
        }
    }

    public MusicbrainzReleaseSearchResult searchReleases(final String artist, final String album, final SearchOptions... opts) {

        final String query = URLEncoder.encode(buildQuery(album, artist), StandardCharsets.UTF_8);
        final MusicbrainzReleaseSearchResult memo = searchStore.get(query.replace("*", ""));
        if (memo != null) {
            return SearchOptions.applyMany(memo, opts);
        }

        final ClassicHttpRequest req = ClassicRequestBuilder
                .get(STR."\{BASE_URL}/release?query=\{query}&fmt=json")
                .build();
        final MusicbrainzReleaseSearchResult result = executeUnchecked(req, resp ->
                mapper.readValue(resp.getEntity().getContent(), MusicbrainzReleaseSearchResult.class));
        searchStore.put(query.replace("*", ""), result);

        return SearchOptions.applyMany(result, opts);
    }

    public MusicbrainzRecording getRecording(final UUID mbId) {

        final String query = mbId.toString();
        final MusicbrainzRecording memo = mbRecordingStore.get(query);
        if (memo != null) {
            return memo;
        }

        final ClassicHttpRequest req = ClassicRequestBuilder
                .get(STR."\{BASE_URL}/release/\{query}?inc=recordings&fmt=json")
                .build();
        final MusicbrainzRecording result = executeUnchecked(req, resp ->
                mapper.readValue(resp.getEntity().getContent(), MusicbrainzRecording.class));
        mbRecordingStore.put(query, result);
        return result;
    }

    private String buildQuery(String album, String artist) {
        switch (QUERY_STRATEGY) {
            case "FIELD_SEARCH": return STR."\"\{album}\" AND artist:\"\{artist}\"";
            case "BROAD_SEARCH":
            default:
                return STR."\{album} \{artist}";
        }
    }
}
