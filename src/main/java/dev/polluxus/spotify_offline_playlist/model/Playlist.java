package dev.polluxus.spotify_offline_playlist.model;

import java.util.List;

public record Playlist(
        String name,
        List<PlaylistSong> tracks
) {

    public record PlaylistSong(
            String name,
            List<String> artists,
            PlaylistAlbum playlistAlbum
    ) {}

    public record PlaylistAlbum(
            String name,
            String spotifyId,
            List<String> artists,
            String releaseDate
    ) {}
}
