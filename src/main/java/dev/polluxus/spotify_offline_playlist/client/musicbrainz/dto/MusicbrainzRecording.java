package dev.polluxus.spotify_offline_playlist.client.musicbrainz.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record MusicbrainzRecording(
        String title,
        List<MusicbrainzMedia> media
) {

    public record MusicbrainzMedia(
            @JsonProperty("track-count") int trackCount,
            List<MusicbrainzTrack> tracks
    ) {}

    public record MusicbrainzTrack(
            String number,
            String title
    ) {}
}
