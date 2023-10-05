package dev.polluxus.spotify_offline_playlist;

import au.com.muel.envconfig.EnvConfig;
import dev.polluxus.spotify_offline_playlist.client.musicbrainz.MusicbrainzClient;
import dev.polluxus.spotify_offline_playlist.client.musicbrainz.dto.MusicbrainzReleaseSearchResult;
import dev.polluxus.spotify_offline_playlist.model.Playlist;
import dev.polluxus.spotify_offline_playlist.model.Playlist.PlaylistAlbum;
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
        final MusicbrainzClient musicbrainzClient = MusicbrainzClient.create(config);
        process(playlistId, spotifyService, musicbrainzClient);
    }

    record Deps (SpotifyService spotify) {}

    public static void process(String playlistId, SpotifyService spotifyService, MusicbrainzClient musicbrainzClient) {

        final Playlist p = spotifyService.getPlaylist(playlistId);
        final List<PlaylistAlbum> distinctPlaylistAlbums = p.tracks().stream().map(PlaylistSong::playlistAlbum).distinct().toList();
        distinctPlaylistAlbums.forEach(a -> log.info("{}", a));
        distinctPlaylistAlbums.forEach(a -> {
            final MusicbrainzReleaseSearchResult res = musicbrainzClient.searchReleases(a.artists().get(0), a.name());
            log.info("{}", res);
        });
    }


    // Open a playlist identifier
    // Read the metadata from the playlist
    // Use a music provider to download the files (caching)
    // Organise the files in playlist order
}
