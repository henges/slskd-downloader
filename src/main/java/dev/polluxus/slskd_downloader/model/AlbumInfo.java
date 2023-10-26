package dev.polluxus.slskd_downloader.model;

import dev.polluxus.slskd_downloader.util.Matchers;
import org.apache.commons.text.similarity.LevenshteinDistance;

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
    ) {

        public String numberAndTitle() {
            return STR."\{number} - \{title}";
        }
    }

    public String searchString() {

        return STR."\{String.join(" ", artists)} \{name}";
    }

    // TODO: this could be memoized.
    public boolean hasTrackContainingTitle() {

        return tracks.stream().anyMatch(t -> {
            final LevenshteinDistance d = Matchers.getEditDistanceFunc(t.title);
            return t.title.toLowerCase().contains(name.toLowerCase()) ||
                    d.apply(t.title.toLowerCase(), name.toLowerCase()) != -1;
        });
    }
}
