package dev.polluxus.spotify_offline_playlist.model;

import java.util.List;

public record AlbumInfo(
        String name,
        String spotifyId,
        List<String> tracks,
        List<String> artists
) {
}
