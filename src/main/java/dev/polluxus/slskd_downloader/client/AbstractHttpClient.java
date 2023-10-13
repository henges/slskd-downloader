package dev.polluxus.slskd_downloader.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;

import java.io.IOException;
import java.io.InputStream;

public abstract class AbstractHttpClient {

    private final HttpClient client;
    private final ObjectMapper mapper;

    public AbstractHttpClient(HttpClient client, ObjectMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    protected <T> T doRequest(final ClassicHttpRequest req, final Class<T> klazz) {

        return doRequest(req, 200, klazz);
    }


    protected <T> T doRequest(final ClassicHttpRequest req, final TypeReference<T> typeReference) {

        return doRequest(req, 200, typeReference);
    }

    protected <T> T doRequest(final ClassicHttpRequest req, final int expectedStatus, final Class<T> klazz) {

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

    protected <T> T doRequest(final ClassicHttpRequest req, final int expectedStatus, final TypeReference<T> typeReference) {

        return executeUnchecked(req, resp -> {
            if (resp.getCode() != expectedStatus) {
                throw new RuntimeException("Expected status code " + expectedStatus + ", but got " + resp.getCode());
            }
            return readValueUnchecked(resp.getEntity().getContent(), typeReference);
        });
    }

    protected <T> T executeUnchecked(ClassicHttpRequest req, HttpClientResponseHandler<T> handler) {

        try {
            return client.execute(req, handler);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected <T> T readValueUnchecked(final InputStream in, Class<T> klazz) {

        try {
            return mapper.readValue(in, klazz);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected <T> T readValueUnchecked(final InputStream in, TypeReference<T> klazz) {

        try {
            return mapper.readValue(in, klazz);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected byte[] writeValueAsBytesUnchecked(final Object value) {

        try {
            return mapper.writeValueAsBytes(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
