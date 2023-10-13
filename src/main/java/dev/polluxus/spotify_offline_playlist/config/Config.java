package dev.polluxus.spotify_offline_playlist.config;

import au.com.muel.envconfig.EnvVar;

public interface Config {

    String spotifyClientId();
    String spotifyClientSecret();
    String spotifyPlaylistId();

    @EnvVar(defaultValue = "~/.spotify_offline_playlist")
    String dataDirectory();

    String slskdBaseUrl();
    @EnvVar(defaultValue = "slskd")
    String slskdUsername();
    @EnvVar(defaultValue = "slskd")
    String slskdPassword();
}
