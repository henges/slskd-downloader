package dev.polluxus.slskd_downloader.config;

import java.util.Optional;

/**
 * Defaults every method in Config to throw UnsupportedOperationException,
 * for use in making test `main` methods
 */
public abstract class UoeDefaultConfig implements Config {

    @Override
    public String dataDirectory() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String slskdBaseUrl() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String slskdUsername() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String slskdPassword() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<String> spotifyClientId() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<String> spotifyClientSecret() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<String> spotifyPlaylistId() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<String> fileSource() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<String> rateYourMusicUser() {
        throw new UnsupportedOperationException();
    }

    @Override
    public double rateYourMusicMinRating() {
        throw new UnsupportedOperationException();
    }

    @Override
    public double rateYourMusicMaxRating() {
        throw new UnsupportedOperationException();
    }
}
