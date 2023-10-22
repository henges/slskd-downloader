package dev.polluxus.slskd_downloader.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class AbstractRateLimitedHttpClient extends AbstractHttpClient {

    private static final Logger log = LoggerFactory.getLogger(AbstractRateLimitedHttpClient.class);

    private static final long LOCK_DURATION = 1100L;
    private long lockedUntil = 0;

    public AbstractRateLimitedHttpClient(HttpClient client, ObjectMapper mapper) {
        super(client, mapper);
    }

    protected void acquireLock() {

        final long now = System.currentTimeMillis();
        if (lockedUntil > now) {

            log.debug("{} locked until {}, waiting {} millis before continuing", this.getClass().getSimpleName(), lockedUntil, lockedUntil - now);

            try {
                Thread.sleep(lockedUntil - now);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        lockedUntil = System.currentTimeMillis() + LOCK_DURATION;
    }

    protected <T> T executeUnchecked(ClassicHttpRequest req, HttpClientResponseHandler<T> handler) {

        try {
            acquireLock();
            log.debug("{}", req.getRequestUri());
            return client.execute(req, handler);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
