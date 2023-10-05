package dev.polluxus.spotify_offline_playlist.client.slskd.request;

public record SlskdDownloadRequest(
        String filename,
        long size
) {
}
