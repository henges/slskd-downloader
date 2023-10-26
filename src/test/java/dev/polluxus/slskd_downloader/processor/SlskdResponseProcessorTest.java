package dev.polluxus.slskd_downloader.processor;


import com.fasterxml.jackson.core.type.TypeReference;
import dev.polluxus.slskd_downloader.client.slskd.response.SlskdSearchDetailResponse;
import dev.polluxus.slskd_downloader.config.JacksonConfig;
import dev.polluxus.slskd_downloader.model.AlbumInfo;
import dev.polluxus.slskd_downloader.processor.matcher.MatchStrategyType;
import dev.polluxus.slskd_downloader.util.PrintUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reflections.Reflections;
import org.reflections.scanners.ResourcesScanner;
import org.reflections.scanners.Scanners;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

// TODO
public class SlskdResponseProcessorTest extends AbstractProcessorTest {

    private static Map<String, List<SlskdSearchDetailResponse>> TEST_RESULT_DATA;
    private static Map<String, AlbumInfo> TEST_REQUEST_DATA;

    private SlskdResponseProcessor processor;

    @BeforeAll
    static void init() throws IOException {

        Pattern p = Pattern.compile("(requests|responses)/");
        TEST_REQUEST_DATA = new HashMap<>();
        TEST_RESULT_DATA = new HashMap<>();

        final Set<String> requestNames = new Reflections("requests", Scanners.Resources).getResources(".*");
        final Set<String> responseNames = new Reflections("responses", Scanners.Resources).getResources(".*");

        requestNames.forEach(s -> {
            try {
                AlbumInfo ai = JacksonConfig.MAPPER.readValue(SlskdResponseProcessorTest.class.getResourceAsStream("/" + s), AlbumInfo.class);
                TEST_REQUEST_DATA.put(p.matcher(s).replaceFirst(""), ai);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        responseNames.forEach(s -> {
            try {
                List<SlskdSearchDetailResponse> ai =
                        JacksonConfig.MAPPER.readValue(SlskdResponseProcessorTest.class.getResourceAsStream("/" + s), new TypeReference<>() {});
                TEST_RESULT_DATA.put(p.matcher(s).replaceFirst(""), ai);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        Assertions.assertEquals(TEST_REQUEST_DATA.keySet(), TEST_RESULT_DATA.keySet());
    }

    @BeforeEach
    public void setup() {
        processor = new SlskdResponseProcessor(MatchStrategyType.EDIT_DISTANCE);
    }

    @Test
    public void testProcessor_dataPreview() {

        TEST_REQUEST_DATA.keySet().stream()
//                .filter(k -> k.equals("Ann Gore-Sunblind.json"))
                .map(k -> processor.process(TEST_RESULT_DATA.get(k), TEST_REQUEST_DATA.get(k)))
                // Find results where the best candidate doesn't have the right # of tracks
                .filter(r -> !r.userResults().isEmpty() && r.userResults().getFirst().bestCandidates().size() == r.albumInfo().tracks().size())
                // Find results where the best candidate has a score less than 1.0
                .filter(r -> !r.userResults().isEmpty() && r.userResults().getFirst().scoreOfBestCandidates() < 1.0)
                .sorted(Comparator.comparing(r -> r.userResults().getFirst().scoreOfBestCandidates()))
                .forEach(res -> {

                    System.out.println(STR."For query \{res.albumInfo().searchString()}");
                    System.out.println("Desired tracklist is:");
                    for (var t : res.albumInfo().tracks()) {
                        System.out.printf("\t%s - %s\n", t.number(), t.title());
                    }
                    System.out.println(STR."(\{res.albumInfo().tracks().size()} tracks total)");
                    if (res.userResults().isEmpty()) {
                        System.out.println("But there are no results for this query");
                    } else for (int i = 0; i < 1 && i < res.userResults().size(); i++) {
                        System.out.println(STR."Result \{i+1} for user \{res.userResults().get(i).username()}");
                        System.out.println(PrintUtils.printProcessorUserResult(res.userResults().get(i), res.albumInfo()));
                    }
                    System.out.println("##########################################################");
                });
    }
}
