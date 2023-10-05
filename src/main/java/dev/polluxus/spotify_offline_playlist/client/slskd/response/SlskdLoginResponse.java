package dev.polluxus.spotify_offline_playlist.client.slskd.response;

public record SlskdLoginResponse(
        int expires,
        int issued,
        String name,
        int notBefore,
        String token,
        String tokenType
) {
}
