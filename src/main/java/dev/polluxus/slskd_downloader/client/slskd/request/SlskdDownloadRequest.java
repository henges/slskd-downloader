package dev.polluxus.slskd_downloader.client.slskd.request;

public record SlskdDownloadRequest(
        String filename,
        long size
) {
}
