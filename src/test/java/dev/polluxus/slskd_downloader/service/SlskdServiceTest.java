package dev.polluxus.slskd_downloader.service;

import dev.polluxus.slskd_downloader.client.slskd.SlskdClient;
import dev.polluxus.slskd_downloader.client.slskd.response.SlskdGetDownloadResponse;
import dev.polluxus.slskd_downloader.client.slskd.response.SlskdGetDownloadResponse.SlskdDownloadDirectoryResponse;
import dev.polluxus.slskd_downloader.client.slskd.response.SlskdGetDownloadResponse.SlskdDownloadFileResponse;
import dev.polluxus.slskd_downloader.config.Config;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static dev.polluxus.slskd_downloader.service.SlskdService.MAX_CONCURRENT_ACTIVE_DOWNLOADS;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SlskdServiceTest {

    private final SlskdClient client = mock(SlskdClient.class);

    private SlskdService service;

    @Before
    public void setup() {
        service = new SlskdService(client);
    }

    @Test
    public void test_downloadSynchronisation() {
        final List<SlskdGetDownloadResponse> busyResponse = IntStream.range(0, MAX_CONCURRENT_ACTIVE_DOWNLOADS)
                .mapToObj(i -> new SlskdGetDownloadResponse("any", List.of(
                        new SlskdDownloadDirectoryResponse("any", 1, List.of(
                                minimalResponse("InProgress"))))))
                .toList();
        // Return busy three times, then allow it
        when(client.getAllDownloads()).thenReturn(busyResponse, busyResponse, busyResponse, List.of());

        final long startMillis = System.currentTimeMillis();

        service.start();

        final boolean result = service.initiateDownloads("any", List.of());
        assertTrue(result);
        // Should have taken roughly three seconds to reach this point in the control flow
        assertTrue(System.currentTimeMillis() - startMillis > 2900);
    }

    private static SlskdDownloadFileResponse minimalResponse(final String state) {

        return new SlskdDownloadFileResponse(null, null,
                null, null, 0, 0, state,
                null, null, 0, 0, null,
                0, 0);
    }
}
