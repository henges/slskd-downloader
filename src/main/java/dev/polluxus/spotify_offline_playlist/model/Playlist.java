package dev.polluxus.spotify_offline_playlist.model;

import java.util.List;

public record Playlist(
        String name,
        List<PlaylistSong> tracks
) {

    public record PlaylistSong(
            String name,
            List<String> artists,
            Album album
    ) {}

    public record Album(
            String name,
            List<String> artists,
            String releaseDate
    ) {}
}
