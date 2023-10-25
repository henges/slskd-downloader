package dev.polluxus.slskd_downloader.processor;


import com.fasterxml.jackson.core.type.TypeReference;
import dev.polluxus.slskd_downloader.client.slskd.response.SlskdSearchDetailResponse;
import dev.polluxus.slskd_downloader.config.JacksonConfig;
import dev.polluxus.slskd_downloader.model.AlbumInfo;
import dev.polluxus.slskd_downloader.processor.matcher.MatchStrategyType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// TODO
public class SlskdResponseProcessorTest extends AbstractProcessorTest {

    private static Map<String, List<SlskdSearchDetailResponse>> TEST_RESULT_DATA;
    private static Map<String, AlbumInfo> TEST_REQUEST_DATA;

    private SlskdResponseProcessor processor;

    @BeforeAll
    static void init() throws IOException {

        TEST_REQUEST_DATA = new HashMap<>();
        TEST_RESULT_DATA = new HashMap<>();
        final AlbumInfo demdikeRequest = JacksonConfig.MAPPER
                .readValue(SlskdResponseProcessorTest.class.getResource("/slskd-processor-request-demdike-tryptych.json"), new TypeReference<>() {});
        final List<SlskdSearchDetailResponse> demdikeResult = JacksonConfig.MAPPER
                .readValue(SlskdResponseProcessorTest.class.getResource("/slskd-processor-response-demdike-tryptych.json"), new TypeReference<>() {});

        TEST_REQUEST_DATA.put("demdike", demdikeRequest);
        TEST_RESULT_DATA.put("demdike", demdikeResult);
    }

    @BeforeEach
    public void setup() {
        processor = new SlskdResponseProcessor(MatchStrategyType.EDIT_DISTANCE);
    }

//    @Test
//    public void test_gen() throws JsonProcessingException {
//
//        String json = "{\"title\":\"Tryptych\",\"media\":[{\"track-count\":3,\"tracks\":[{\"number\":\"1\",\"title\":\"Forest of Evil (Dusk)\"},{\"number\":\"2\",\"title\":\"Forest of Evil (Dawn)\"},{\"number\":\"3\",\"title\":\"Quiet Sky\"}]},{\"track-count\":9,\"tracks\":[{\"number\":\"1\",\"title\":\"Gaged in Stammheim\"},{\"number\":\"2\",\"title\":\"Eurydice\"},{\"number\":\"3\",\"title\":\"Regolith\"},{\"number\":\"4\",\"title\":\"The Stars Are Moving\"},{\"number\":\"5\",\"title\":\"Bardo Thodol\"},{\"number\":\"6\",\"title\":\"Matilda's Dream\"},{\"number\":\"7\",\"title\":\"Nothing but the Night 2\"},{\"number\":\"8\",\"title\":\"Library of Solomon Book 1\"},{\"number\":\"9\",\"title\":\"Library of Solomon Book 2\"}]},{\"track-count\":11,\"tracks\":[{\"number\":\"1\",\"title\":\"Black Sun\"},{\"number\":\"2\",\"title\":\"Hashshashin Chant\"},{\"number\":\"3\",\"title\":\"Repository of Light\"},{\"number\":\"4\",\"title\":\"Of Decay & Shadows\"},{\"number\":\"5\",\"title\":\"Rain & Shame\"},{\"number\":\"6\",\"title\":\"Desert Ascetic\"},{\"number\":\"7\",\"title\":\"Viento De Levante\"},{\"number\":\"8\",\"title\":\"Leptonic Matter\"},{\"number\":\"9\",\"title\":\"A Tale of Sand\"},{\"number\":\"10\",\"title\":\"Filtered Through Prejudice\"},{\"number\":\"11\",\"title\":\"Past Is Past\"}]}]}";
//        MusicbrainzRecording recording = JacksonConfig.MAPPER.readValue(json, MusicbrainzRecording.class);
//
//        final List<AlbumTrack> tracks = recording.media().stream().flatMap(m -> m.tracks().stream()).map(t -> new AlbumTrack(t.number(), t.title())).toList();
//        AlbumInfo ai = new AlbumInfo("Tryptych", null, tracks, List.of("Demdike Stare"));
//        System.out.println(JacksonConfig.MAPPER.writeValueAsString(ai));
//    }

    @Test
    public void testProcessor_demdike() {

        final var res = processor.process(TEST_RESULT_DATA.get("demdike"), TEST_REQUEST_DATA.get("demdike"));
        System.out.println("");
    }

//    @Test
//    public void test() {
//
//        var processor = new SlskdResponseProcessor(MatchStrategyType.EDIT_DISTANCE);
//        var reses = processor.process(responses, albumInfo);
//
//        reses.userResults().stream().forEach(pur -> {
//            System.out.printf("User %s, score %f:\n\n", pur.username(), pur.scoreOfBestCandidates());
//            pur.bestCandidates().stream().forEach(pfr -> {
//                System.out.printf("%s - score %f\n", pfr.originalData().filename(), pfr.score());
//            });
//        });
//
//        System.out.println("");
//    }
}
