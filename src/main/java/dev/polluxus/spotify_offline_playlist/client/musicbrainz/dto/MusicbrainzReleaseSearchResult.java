package dev.polluxus.spotify_offline_playlist.client.musicbrainz.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * See <a href="https://musicbrainz.org/doc/MusicBrainz_API/Search#Release">docs</a>
 */
public record MusicbrainzReleaseSearchResult(
        String created,
        int count,
        int offset,
        List<MusicbrainzRelease> releases
) {

    public record MusicbrainzRelease(
            String id,
            String title,
            String disambiguation,
            @JsonProperty("artist-credit") MusicbrainzArtistCredit artistCredit,
            String date,
            @JsonProperty("track-count") int trackCount
    ) { }

    public record MusicbrainzArtistCredit(
            String name,
            MusicbrainzArtist artist
    ) {}

    public record MusicbrainzArtist(
            String id,
            String name,
            @JsonProperty("sort-name") String sortName
    ) {}
}
