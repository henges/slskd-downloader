package dev.polluxus.slskd_downloader;

import au.com.muel.envconfig.EnvConfig;
import dev.polluxus.slskd_downloader.client.musicbrainz.MusicbrainzClient;
import dev.polluxus.slskd_downloader.client.plex.PlexClient;
import dev.polluxus.slskd_downloader.client.rym.RateYourMusicClient;
import dev.polluxus.slskd_downloader.client.slskd.SlskdClient;
import dev.polluxus.slskd_downloader.client.spotify.SpotifyClient;
import dev.polluxus.slskd_downloader.config.Config;

public class ClientTester {

    /**
     * Set a breakpoint on the final line and run this main method in a debugger.
     * You can then use the HTTP clients from this project interactively.
     * @param args
     */
    public static void main(String[] args) {

        Config config = EnvConfig.fromEnv(Config.class);

        SlskdClient slskdClient = SlskdClient.create(config);
        MusicbrainzClient musicbrainzClient = MusicbrainzClient.create(config);
        RateYourMusicClient rateYourMusicClient = RateYourMusicClient.create(config);
        SpotifyClient spotifyClient = SpotifyClient.create(config);
        PlexClient plexClient = PlexClient.create(config);
        System.out.println("Clients acquired.");
    }
}
