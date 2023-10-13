package dev.polluxus.spotify_offline_playlist.model;

import javax.annotation.Nullable;
import java.util.List;

public record AlbumInfo(
        String name,
        @Nullable String spotifyId,
        List<String> tracks,
        List<String> artists
) {

    public String searchString() {

        return String.join(" ", artists) + " " + name;
    }
}
