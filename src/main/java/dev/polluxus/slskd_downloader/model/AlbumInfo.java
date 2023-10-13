package dev.polluxus.slskd_downloader.model;

import javax.annotation.Nullable;
import java.util.List;

public record AlbumInfo(
        String name,
        @Nullable String spotifyId,
        List<AlbumTrack> tracks,
        List<String> artists
) {

    public record AlbumTrack(
            String number,
            String title
    ) {}

    public String searchString() {

        return String.join(" ", artists) + " " + name;
    }
}
