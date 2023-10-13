package dev.polluxus.slskd_downloader.config;

import au.com.muel.envconfig.EnvVar;

import java.util.Optional;

public interface Config {

    @EnvVar(defaultValue = "~/.spotify_offline_playlist")
    String dataDirectory();
    String slskdBaseUrl();
    @EnvVar(defaultValue = "slskd")
    String slskdUsername();
    @EnvVar(defaultValue = "slskd")
    String slskdPassword();

    Optional<String> spotifyClientId();
    Optional<String> spotifyClientSecret();
    Optional<String> spotifyPlaylistId();

    Optional<String> fileSource();


}
