package dev.polluxus.spotify_offline_playlist.processor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.polluxus.spotify_offline_playlist.client.slskd.response.SlskdSearchDetailResponse;

import java.io.IOException;
import java.util.List;

public class SlskdResponseProcessorTest {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final List<SlskdSearchDetailResponse> responses;
    static {
        try {
            responses = mapper.readValue(SlskdResponseProcessorTest.class.getResourceAsStream("/slskd-processor-test-data.json"), new TypeReference<>() {});
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


}
