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

    Optional<String> fileSource();

    Optional<String> spotifyClientId();
    Optional<String> spotifyClientSecret();
    Optional<String> spotifyPlaylistId();

    Optional<String> rateYourMusicUser();

    @EnvVar(defaultValue = "3.5")
    double rateYourMusicMinRating();

    @EnvVar(defaultValue = "5.0")
    double rateYourMusicMaxRating();

    Optional<String> blacklistedUsersFile();

    Optional<String> plexBaseUrl();

    Optional<String> plexToken();

    Optional<String> plexLibrarySectionId();
}
