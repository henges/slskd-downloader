package dev.polluxus.slskd_downloader.client.slskd.response;

public record SlskdLoginResponse(
        int expires,
        int issued,
        String name,
        int notBefore,
        String token,
        String tokenType
) {
}
