package dev.polluxus.spotify_offline_playlist.processor.matcher;

import dev.polluxus.spotify_offline_playlist.client.slskd.response.SlskdSearchDetailResponse;
import dev.polluxus.spotify_offline_playlist.client.slskd.response.SlskdSearchDetailResponse.SlskdSearchMatchResponse;
import dev.polluxus.spotify_offline_playlist.model.AlbumInfo;
import dev.polluxus.spotify_offline_playlist.processor.model.ProcessorFileResult;
import dev.polluxus.spotify_offline_playlist.processor.model.ProcessorFileResultBuilder;
import dev.polluxus.spotify_offline_playlist.processor.model.ProcessorMatchDetailsBuilder;
import dev.polluxus.spotify_offline_playlist.util.FilenameUtils;
import org.apache.commons.text.similarity.LevenshteinDistance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class EditDistanceStrategy implements MatchStrategy {

    private static final Pattern TRACK_NUMBER_PATTERN = Pattern.compile("^\\d+(\s|\\.|-)*");
    private static final LevenshteinDistance LEVENSHTEIN_DISTANCE = new LevenshteinDistance(4);

    private String sanitiseFilename(final String filename, final AlbumInfo albumInfo) {

        final String remoteFilename = FilenameUtils.getName(filename);
        final String remoteBaseName = FilenameUtils.getBaseName(remoteFilename);
        final String stripArtistName = albumInfo.artists().stream().reduce(remoteBaseName, (old, in) -> old.replaceAll(in, ""));
        final String strippedTrackNumber = TRACK_NUMBER_PATTERN.matcher(stripArtistName).replaceFirst("");

        return strippedTrackNumber;
    }

    @Override
    public Map<String, List<ProcessorFileResultBuilder>> apply(SlskdSearchDetailResponse resp, AlbumInfo albumInfo) {

        Map<String, List<ProcessorFileResultBuilder>> matchesForPattern = new HashMap<>(albumInfo.tracks().size());
        for (SlskdSearchMatchResponse currentResult : resp.files()) {

            final String originalFilename = currentResult.filename();
            final String sanitisedFileName = sanitiseFilename(originalFilename, albumInfo);

            for (String currentTarget : albumInfo.tracks()) {
                final int distance = LEVENSHTEIN_DISTANCE.apply(currentTarget.toLowerCase(), sanitisedFileName.toLowerCase());
                if (distance != -1) {

                    var pr = ProcessorFileResultBuilder.builder()
                            .originalData(currentResult)
                            .matchDetails(ProcessorMatchDetailsBuilder.builder()
                                    .matchesTitle(currentTarget)
                                    .distance(distance)
                                    .build());
                    matchesForPattern.computeIfAbsent(currentTarget, (k) -> new ArrayList<>()).add(pr);
                }
            }
        }

        return matchesForPattern;
    }
}
