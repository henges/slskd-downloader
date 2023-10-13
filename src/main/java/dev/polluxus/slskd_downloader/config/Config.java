package dev.polluxus.slskd_downloader.config;

import au.com.muel.envconfig.EnvVar;

public interface Config {

    String spotifyClientId();
    String spotifyClientSecret();
    String spotifyPlaylistId();

    @EnvVar(defaultValue = "~/.spotify_offline_playlist")
    String dataDirectory();
    String fileSource();

    String slskdBaseUrl();
    @EnvVar(defaultValue = "slskd")
    String slskdUsername();
    @EnvVar(defaultValue = "slskd")
    String slskdPassword();


}
