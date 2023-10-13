package dev.polluxus.spotify_offline_playlist.processor.matcher;

import dev.polluxus.spotify_offline_playlist.client.slskd.response.SlskdSearchDetailResponse;
import dev.polluxus.spotify_offline_playlist.client.slskd.response.SlskdSearchDetailResponse.SlskdSearchMatchResponse;
import dev.polluxus.spotify_offline_playlist.model.AlbumInfo;
import dev.polluxus.spotify_offline_playlist.processor.model.ProcessorFileResult;
import dev.polluxus.spotify_offline_playlist.processor.model.ProcessorFileResultBuilder;
import dev.polluxus.spotify_offline_playlist.processor.model.ProcessorMatchDetailsBuilder;
import org.apache.commons.text.similarity.LevenshteinDistance;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EditDistanceStrategy implements MatchStrategy {

    private static final LevenshteinDistance LEVENSHTEIN_DISTANCE = new LevenshteinDistance(4);

    @Override
    public Map<String, List<ProcessorFileResult>> apply(SlskdSearchDetailResponse resp, AlbumInfo albumInfo) {

        Map<String, List<ProcessorFileResult>> matchesForPattern = new HashMap<>(albumInfo.tracks().size());
        for (SlskdSearchMatchResponse currentResult : resp.files()) {
            final String remoteFilename = Path.of(currentResult.filename()).getFileName().toString();
            for (String currentTarget : albumInfo.tracks()) {
                final int distance = LEVENSHTEIN_DISTANCE.apply(currentTarget, remoteFilename);
                if (distance != -1) {

                    var pr = ProcessorFileResultBuilder.builder()
                            .filename(currentResult.filename())
                            .matchDetails(ProcessorMatchDetailsBuilder.builder()
                                    .matchesTitle(currentTarget)
                                    .distance(distance)
                                    .build())
                            .isTargetFormat(FILE_FORMAT_PATTERN.matcher(currentResult.filename()).find())
                            // Almost all audio files will be at least 500kb and this helps
                            // filter out e.g. tiny metadata files that contain the song name
                            .sizeOk(currentResult.size() > 500_000)
                            .build();
                    matchesForPattern.computeIfAbsent(currentTarget, (k) -> new ArrayList<>()).add(pr);
                }
            }
        }

        return matchesForPattern;
    }
}
