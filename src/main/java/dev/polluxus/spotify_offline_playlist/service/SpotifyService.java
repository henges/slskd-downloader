package dev.polluxus.spotify_offline_playlist.service;

import dev.polluxus.spotify_offline_playlist.Config;
import dev.polluxus.spotify_offline_playlist.client.SpotifyClient;
import dev.polluxus.spotify_offline_playlist.model.Playlist;
import dev.polluxus.spotify_offline_playlist.model.Playlist.Album;
import dev.polluxus.spotify_offline_playlist.model.Playlist.PlaylistSong;
import dev.polluxus.spotify_offline_playlist.store.FileBackedStore;
import se.michaelthelin.spotify.model_objects.specification.AlbumSimplified;
import se.michaelthelin.spotify.model_objects.specification.ArtistSimplified;
import se.michaelthelin.spotify.model_objects.specification.PlaylistTrack;
import se.michaelthelin.spotify.model_objects.specification.Track;

import java.util.Arrays;
import java.util.List;

public class SpotifyService {

    private SpotifyClient spotifyClient;
    private final Config config;
    private final FileBackedStore<Playlist> store;

    public SpotifyService(Config config) {
        this.config = config;
        this.store = FileBackedStore.from(config, Playlist.class);
    }

    private void initClient() {
        if (spotifyClient == null) {
            spotifyClient = SpotifyClient.create(config);
        }
    }

    public Playlist getPlaylist(String playlistId) {

        final Playlist memo = store.get(playlistId);
        if (memo != null) {
            return memo;
        }

        final Playlist result = getPlaylist(config, playlistId);
        store.put(playlistId, result);
        return result;
    }

    private Playlist getPlaylist(Config config, String playlistId) {

        initClient();
        final String playlistName = spotifyClient.getPlaylist(playlistId).getName();
        List<PlaylistTrack> playlistTracks = spotifyClient.getAllPlaylistItems(playlistId);
        final List<PlaylistSong> songs = playlistTracks.stream().map(p -> {
            Track t = (Track) p.getTrack();
            final String trackName = t.getName();
            final List<String> trackArtists = Arrays.stream(t.getArtists()).map(ArtistSimplified::getName).toList();
            final AlbumSimplified album = t.getAlbum();
            final String albumName = album.getName();
            final List<String> albumArtists = Arrays.stream(album.getArtists()).map(ArtistSimplified::getName).toList();
            final String releaseDate = album.getReleaseDate();
            return new PlaylistSong(trackName, trackArtists, new Album(albumName, albumArtists, releaseDate));
        }).toList();

        return new Playlist(playlistName, songs);
    }
}
