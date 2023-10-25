package dev.polluxus.slskd_downloader.processor.matcher;

import dev.polluxus.slskd_downloader.model.AlbumInfo;
import dev.polluxus.slskd_downloader.processor.model.output.ProcessorFileResultBuilder;
import dev.polluxus.slskd_downloader.processor.model.input.ProcessorInputUser.ProcessorInputDirectory;
import dev.polluxus.slskd_downloader.processor.model.output.ProcessorMatchDetailsBuilder;
import dev.polluxus.slskd_downloader.util.FilenameUtils;
import org.apache.commons.text.similarity.LevenshteinDistance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class EditDistanceStrategy implements MatchStrategy {

    // Matches e.g. '1-01' - for multi disk albums
    private static final Pattern MULTI_DISK_TRACK_NUMBER_PATTERN = Pattern.compile("^\\d+-\\d+\\s+");
    // Generic matcher that tries to match as many 'track number' looking characters at the start of the string
    // Have to be careful to not match a track number that itself is numeric (e.g.: Global Communication's 76:14)
    private static final Pattern GENERIC_TRACK_NUMBER_PATTERN = Pattern.compile("^\\d+(\s|\\.|-)*");
    private static final LevenshteinDistance LEVENSHTEIN_DISTANCE_1 = new LevenshteinDistance(1);
    private static final LevenshteinDistance LEVENSHTEIN_DISTANCE_4 = new LevenshteinDistance(4);

    private String sanitiseFilename(final String filename, final AlbumInfo albumInfo) {

        final String remoteFilename = FilenameUtils.getName(filename);
        final String remoteBaseName = FilenameUtils.getBaseName(remoteFilename);
        final String stripArtistName = albumInfo.artists().stream().reduce(remoteBaseName, (old, in) -> old.replaceAll(in, ""));
        final var multiDiskMatcher = MULTI_DISK_TRACK_NUMBER_PATTERN.matcher(stripArtistName);
        if (multiDiskMatcher.find()) {
            return multiDiskMatcher.replaceFirst("");
        }
        return GENERIC_TRACK_NUMBER_PATTERN.matcher(stripArtistName).replaceFirst("");
    }

    private LevenshteinDistance getEditDistanceFunc(final String trackName) {

        final int length = trackName.length();
        if (length <= 6) {
            return LEVENSHTEIN_DISTANCE_1;
        } else {
            return LEVENSHTEIN_DISTANCE_4;
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
