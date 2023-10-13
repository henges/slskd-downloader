package dev.polluxus.spotify_offline_playlist.service;

import dev.polluxus.spotify_offline_playlist.config.Config;
import dev.polluxus.spotify_offline_playlist.client.SpotifyClient;
import dev.polluxus.spotify_offline_playlist.model.AlbumInfo;
import dev.polluxus.spotify_offline_playlist.model.Playlist;
import dev.polluxus.spotify_offline_playlist.model.Playlist.PlaylistAlbum;
import dev.polluxus.spotify_offline_playlist.model.Playlist.PlaylistSong;
import dev.polluxus.spotify_offline_playlist.store.FileBackedStore;
import se.michaelthelin.spotify.model_objects.specification.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SpotifyService {

    private SpotifyClient spotifyClient;
    private final Config config;
    private final FileBackedStore<Playlist> playlistStore;
    private final FileBackedStore<AlbumInfo> albumStore;

    public SpotifyService(Config config) {
        this.config = config;
        this.playlistStore = FileBackedStore.from(config, Playlist.class);
        this.albumStore = FileBackedStore.from(config, AlbumInfo.class);
    }

    private void initClient() {
        if (spotifyClient == null) {
            spotifyClient = SpotifyClient.create(config);
        }
    }

    public Playlist getPlaylist(String playlistId) {

        final Playlist memo = playlistStore.get(playlistId);
        if (memo != null) {
            return memo;
        }

        final Playlist result = getPlaylistInternal(playlistId);
        playlistStore.put(playlistId, result);
        return result;
    }

    public List<AlbumInfo> getAlbums(final List<String> albumIds) {

        final List<String> toSearch = new ArrayList<>();
        final List<AlbumInfo> results = new ArrayList<>();
        for (var id : albumIds) {
            final AlbumInfo memo = albumStore.get(id);
            if (memo != null) {
                results.add(memo);
            } else {
                toSearch.add(id);
            }
        }

        if (toSearch.size() > 0) {
            initClient();
            final List<AlbumInfo> remoted = spotifyClient.getAlbums(toSearch);
            remoted.forEach(ai -> albumStore.put(ai.spotifyId(), ai));

            results.addAll(remoted);
        }

        return results;
    }

    private Playlist getPlaylistInternal(String playlistId) {

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

            return new PlaylistSong(trackName, trackArtists, new PlaylistAlbum(albumName, album.getId(), albumArtists, releaseDate));
        }).toList();

        return new Playlist(playlistName, songs);
    }
}
