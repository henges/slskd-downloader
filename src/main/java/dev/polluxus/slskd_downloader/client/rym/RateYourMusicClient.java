package dev.polluxus.slskd_downloader.client.rym;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.polluxus.slskd_downloader.client.AbstractHttpClient;
import dev.polluxus.slskd_downloader.client.AbstractRateLimitedHttpClient;
import dev.polluxus.slskd_downloader.config.Config;
import dev.polluxus.slskd_downloader.config.JacksonConfig;
import dev.polluxus.slskd_downloader.config.UoeDefaultConfig;
import dev.polluxus.slskd_downloader.model.AlbumArtistPair;
import dev.polluxus.slskd_downloader.store.FileBackedStore;
import dev.polluxus.slskd_downloader.store.Store;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RateYourMusicClient extends AbstractRateLimitedHttpClient {

    private static final String RYM_BASE_URL = "https://rateyourmusic.com";
    public static final int PAGE_SIZE = 25;

    private final Store<String> resultStore;

    public static void main(String[] args) {
        Config c = new UoeDefaultConfig() {
            @Override
            public String dataDirectory() {
                return "C:\\Users\\alexa\\.spotify_offline_playlist";
            }

            @Override
            public Optional<String> rateYourMusicUser() {
                return Optional.of("hovering");
            }

            @Override
            public double rateYourMusicMinRating() {
                return 3.5;
            }

            @Override
            public double rateYourMusicMaxRating() {
                return 5.0;
            }
        };

        create(c);
    }

    public RateYourMusicClient(HttpClient client, ObjectMapper mapper, Store<String> resultStore) {
        super(client, mapper);
        this.resultStore = resultStore;
    }

    public static RateYourMusicClient create(Config config) {

        HttpClient client = HttpClients.createDefault();
        ObjectMapper mapper = JacksonConfig.MAPPER;
        Store<String> resultStore = FileBackedStore.from(config, String.class);
        return new RateYourMusicClient(client, mapper, resultStore);
    }

    public List<AlbumArtistPair> getAllRatings(final String username, final double lowerBound, final double upperBound) {

        final List<AlbumArtistPair> allRatings = new ArrayList<>();
        List<AlbumArtistPair> currentList;
        int pageNumber = 1;
        do {
            currentList = getRatings(username, lowerBound, upperBound, Optional.of(pageNumber));
            allRatings.addAll(currentList);
            pageNumber++;
        } while (currentList.size() >= PAGE_SIZE);

        return allRatings;
    }

    public List<AlbumArtistPair> getRatings(final String username, final double lowerBound) {

        return getRatings(username, lowerBound, 5.0);
    }

    public List<AlbumArtistPair> getRatings(final String username, final double lowerBound, final double upperBound) {

        return getRatings(username, lowerBound, upperBound, Optional.empty());
    }

    public List<AlbumArtistPair> getRatings(final String username, final double lowerBound, final double upperBound, Optional<Integer> pageNumber) {

        final Optional<String> formattedPageNumber = pageNumber.map(p -> STR."\{p}");

        final String url =
                STR."\{RYM_BASE_URL}/collection/\{username}/r\{lowerBound}-\{upperBound}\{formattedPageNumber.map(f -> "/" + f).orElse("")}";
        final String key = STR."RYM-\{username}-\{lowerBound}-\{upperBound}-\{formattedPageNumber.orElse("1")}";

        if (resultStore.has(key)) {
            return process(resultStore.get(key));
        }

        final ClassicHttpRequest req = ClassicRequestBuilder.get(url).build();
        return executeUnchecked(req, (resp) -> {
            validateStatusCode(200, resp);
            String s = new String(resp.getEntity().getContent().readAllBytes());
            resultStore.put(key, s);
            return process(s);
        });
    }

    private List<AlbumArtistPair> process(String html) {
        Document d = Jsoup.parse(html);
        return d.getElementsByClass("or_q_albumartist").stream()
                .map(e -> new AlbumArtistPair(
                        e.getElementsByClass("artist").text(),
                        e.getElementsByClass("album").text())
                )
                .toList();
    }
}
