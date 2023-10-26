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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static dev.polluxus.slskd_downloader.util.Matchers.*;

public class EditDistanceStrategy implements MatchStrategy {

    private static final Logger log = LoggerFactory.getLogger(EditDistanceStrategy.class);

    private static final ConcurrentMap<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>();

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
                (old, in) -> PATTERN_CACHE.computeIfAbsent(in, n -> Pattern.compile(Pattern.quote(n), Pattern.CASE_INSENSITIVE))
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

    private String maybeStripAlbumName(final String filename, AlbumInfo albumInfo) {

        // If there's a track on this album that contains the title of the album,
        // we expect the album title to occur at most once in the filename
        final int expectedOccurrences = albumInfo.hasTrackContainingTitle() ? 1 : 0;
        final Pattern p = PATTERN_CACHE.computeIfAbsent(albumInfo.name(), n -> Pattern.compile(Pattern.quote(n), Pattern.CASE_INSENSITIVE));
        long titleMatches = p.matcher(filename).results().count();
        String strippedName = filename;
        // Presumably the user has prefixed the track title with the album name, so just
        // replace the first instance. It's possible to have a track name where the title
        // is repeated more than once, so if we replace any more, we might miss matches.
        if (titleMatches > expectedOccurrences) {
            strippedName = p.matcher(strippedName).replaceFirst("");
        }
        final String afterTrackNumberMatchers = applyTrackNumberMatchers(strippedName);
        // It's likely that the artist name is also the track name
        if (afterTrackNumberMatchers.isEmpty()) {
            log.trace("Not stripping album name ({}) from track {} because it would be empty after sanitisation!",
                    albumInfo.name(), filename);
            return filename;
        }

        return strippedName;
    }

    private String sanitiseFilename(final String filename, final AlbumInfo albumInfo) {

        // Using Optional here makes it easier to tweak which stages are included
        final String ret = Optional.of(filename)
                .map(FilenameUtils::getBaseName)
                .map(StringEscapeUtils::unescapeHtml4)
                .map(n -> maybeStripArtistName(n, albumInfo.artists()))
                .map(n -> maybeStripAlbumName(n, albumInfo))
                .map(n -> LEADING_GARBAGE.matcher(n).replaceFirst(""))
                .map(this::applyTrackNumberMatchers)
                .map(n -> LEADING_GARBAGE.matcher(n).replaceFirst(""))
                .map(n -> FEATURED_ARTIST_MATCHER.matcher(n).replaceAll(""))
                .map(n -> n.replaceAll("’", "'"))
                .orElseThrow();

        return ret;
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
