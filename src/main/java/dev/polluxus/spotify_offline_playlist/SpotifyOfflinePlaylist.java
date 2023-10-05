package dev.polluxus.spotify_offline_playlist;

import au.com.muel.envconfig.EnvConfig;
import dev.polluxus.spotify_offline_playlist.model.Playlist;
import dev.polluxus.spotify_offline_playlist.model.Playlist.Album;
import dev.polluxus.spotify_offline_playlist.model.Playlist.PlaylistSong;
import dev.polluxus.spotify_offline_playlist.service.SpotifyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class SpotifyOfflinePlaylist {

    private static final Logger log = LoggerFactory.getLogger(SpotifyOfflinePlaylist.class);

    public static void main(String[] args) {

        final Config config = EnvConfig.fromEnv(Config.class);
        final String playlistId = config.spotifyPlaylistId();
        final SpotifyService spotifyService = new SpotifyService(config);
        process(playlistId, spotifyService);
    }

    record Deps (SpotifyService spotify) {}

    public static void process(String playlistId, SpotifyService spotifyService) {

        final Playlist p = spotifyService.getPlaylist(playlistId);
        final List<Album> distinctAlbums = p.tracks().stream().map(PlaylistSong::album).distinct().toList();
        distinctAlbums.forEach(a -> log.info("{}", a));
    }


    // Open a playlist identifier
    // Read the metadata from the playlist
    // Use a music provider to download the files (caching)
    // Organise the files in playlist order
}
