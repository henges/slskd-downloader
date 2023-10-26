package dev.polluxus.slskd_downloader.processor.matcher;

import dev.polluxus.slskd_downloader.model.AlbumInfo;
import dev.polluxus.slskd_downloader.processor.model.output.ProcessorFileResultBuilder;
import dev.polluxus.slskd_downloader.processor.model.input.ProcessorInputUser.ProcessorInputDirectory;
import dev.polluxus.slskd_downloader.processor.model.output.ProcessorMatchDetailsBuilder;
import dev.polluxus.slskd_downloader.util.FilenameUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

import static dev.polluxus.slskd_downloader.util.Matchers.*;

public class EditDistanceStrategy implements MatchStrategy {

    private static final Logger log = LoggerFactory.getLogger(EditDistanceStrategy.class);

    private static final ConcurrentMap<String, Pattern> artistNamePatternCache = new ConcurrentHashMap<>();

    private static final LevenshteinDistance LEVENSHTEIN_DISTANCE_1 = new LevenshteinDistance(1);
    private static final LevenshteinDistance LEVENSHTEIN_DISTANCE_4 = new LevenshteinDistance(4);
    private static final LevenshteinDistance LEVENSHTEIN_DISTANCE_8 = new LevenshteinDistance(8);

    private String applyTrackNumberMatchers(final String filename) {

        final var multiDiskMatcher = MULTI_DISK_TRACK_NUMBER_PATTERN.matcher(filename);
        if (multiDiskMatcher.find()) {
            return multiDiskMatcher.replaceFirst("");
        }
        final var vinylMatcher = VINYL_SIDE_TRACK_NUMBER_PATTERN.matcher(filename);
        if (vinylMatcher.find()) {
            return vinylMatcher.replaceFirst("");
        }
        return GENERIC_TRACK_NUMBER_PATTERN.matcher(filename).replaceFirst("");
    }

    private String maybeStripArtistName(final String filename, List<String> artists) {

        final String after = artists.stream().reduce(filename,
                (old, in) -> artistNamePatternCache.computeIfAbsent(in, n -> Pattern.compile(n, Pattern.CASE_INSENSITIVE))
                        .matcher(old).replaceFirst(""));
        final String afterTrackNumberMatchers = applyTrackNumberMatchers(after);
        // It's likely that the artist name is also the track name
        if (after.length() != filename.length() && afterTrackNumberMatchers.isEmpty()) {
            log.trace("Not stripping artist names ({}) from track {} because it would be empty after sanitisation!",
                    artists.stream().reduce((u1, u2) -> STR."\{u1} \{u2}").orElse(""), filename);
            return filename;
        }

        return after;
    }

    private String sanitiseFilename(final String filename, final AlbumInfo albumInfo) {

        final String remoteBaseName = FilenameUtils.getBaseName(filename);
        final String unescaped = StringEscapeUtils.unescapeHtml4(remoteBaseName);
        final String stripArtistName = maybeStripArtistName(unescaped, albumInfo.artists());
        final String stripTrackNumber = applyTrackNumberMatchers(stripArtistName);
        final String stripGarbage = LEADING_GARBAGE.matcher(stripTrackNumber).replaceFirst("");
        final String stripFeature = FEATURED_ARTIST_MATCHER.matcher(stripGarbage).replaceAll("");
        return stripFeature
                .replaceAll("’", "'");
    }

    private LevenshteinDistance getEditDistanceFunc(final String trackName) {

        final int length = trackName.length();
        if (length <= 6) {
            return LEVENSHTEIN_DISTANCE_1;
        } else if (length <= 25) {
            return LEVENSHTEIN_DISTANCE_4;
        } else {
            return LEVENSHTEIN_DISTANCE_8;
        }
    }

    @Override
    public Map<String, List<ProcessorFileResultBuilder>> apply(ProcessorInputDirectory resp, AlbumInfo albumInfo) {

        Map<String, List<ProcessorFileResultBuilder>> matchesForPattern = new HashMap<>(albumInfo.tracks().size());
        for (var currentResult : resp.files()) {

            final String originalFilename = currentResult.filename();
            final String sanitisedFileName = sanitiseFilename(originalFilename, albumInfo);

            for (var currentTrack : albumInfo.tracks()) {
                final String currentTarget = currentTrack.title();
                final LevenshteinDistance distanceFunc = getEditDistanceFunc(currentTarget);
                final int distance;
                if (currentTarget.equalsIgnoreCase(sanitisedFileName)) {
                    distance = 0;
                } else {
                    distance = distanceFunc.apply(currentTarget.toLowerCase(), sanitisedFileName.toLowerCase());
                }
                if (distance != -1) {

                    var pr = ProcessorFileResultBuilder.builder()
                            .originalData(currentResult.originalData())
                            .matchDetails(ProcessorMatchDetailsBuilder.builder()
                                    .matchesTitle(currentTarget)
                                    .matchesNumber(currentTrack.number())
                                    .distance(distance)
                                    .build());
                    matchesForPattern.computeIfAbsent(currentTrack.numberAndTitle(), (k) -> new ArrayList<>()).add(pr);
                }
            }
        }

        return matchesForPattern;
    }
}
