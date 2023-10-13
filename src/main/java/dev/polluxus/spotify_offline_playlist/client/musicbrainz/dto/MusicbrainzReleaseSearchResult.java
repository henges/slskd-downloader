package dev.polluxus.spotify_offline_playlist.client.musicbrainz.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

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
            UUID id,
            String title,
            String disambiguation,
            @JsonProperty("artist-credit") List<MusicbrainzArtistCredit> artistCredit,
            LocalDate date,
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
