package dev.polluxus.spotify_offline_playlist.processor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.polluxus.spotify_offline_playlist.client.slskd.response.SlskdSearchDetailResponse;
import dev.polluxus.spotify_offline_playlist.config.JacksonConfig;
import dev.polluxus.spotify_offline_playlist.model.AlbumInfo;

import java.io.IOException;
import java.util.List;

public abstract class AbstractProcessorTest {

    protected static final ObjectMapper mapper = JacksonConfig.MAPPER;
    protected static final List<SlskdSearchDetailResponse> responses;
    protected static final AlbumInfo albumInfo;
    static {
        try {
            responses = mapper.readValue(SlskdResponseProcessorTest.class.getResourceAsStream("/slskd-processor-test-response-1.json"), new TypeReference<>() {});
            albumInfo = mapper.readValue(SlskdResponseProcessorTest.class.getResourceAsStream("/slskd-processor-test-request-1.json"), new TypeReference<>() {});
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
