package dev.polluxus.spotify_offline_playlist.processor;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import dev.polluxus.spotify_offline_playlist.client.slskd.response.SlskdSearchDetailResponse;
import dev.polluxus.spotify_offline_playlist.model.AlbumInfo;
import dev.polluxus.spotify_offline_playlist.processor.matcher.MatchStrategyType;
import dev.polluxus.spotify_offline_playlist.processor.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static dev.polluxus.spotify_offline_playlist.processor.matcher.MatchStrategy.FILE_FORMAT_PATTERN;

public class SlskdResponseProcessor {

    private static final Logger log = LoggerFactory.getLogger(SlskdResponseProcessor.class);

    private final MatchStrategyType matchStrategy;

    public SlskdResponseProcessor(MatchStrategyType matchStrategy) {
        this.matchStrategy = matchStrategy;
    }

    public ProcessorSearchResult process(List<SlskdSearchDetailResponse> resps, AlbumInfo albumInfo) {

        return new ProcessorSearchResult(albumInfo, resps.stream()
                .map(r -> findMatches(r, albumInfo))
                .map(this::computeBestFiles)
                .map(r -> scoreUser(r, albumInfo))
                .map(ProcessorUserResultBuilder::build)
                .filter(ur -> !ur.byTrackName().isEmpty())
                .sorted(Comparator.comparing(ProcessorUserResult::scoreOfBestCandidates).reversed())
                .toList());
    }

    private ProcessorUserResultBuilder findMatches(SlskdSearchDetailResponse resp, AlbumInfo albumInfo) {

        final Map<String, List<ProcessorFileResultBuilder>> matches = matchStrategy.match(resp, albumInfo);
        matches.values().stream().flatMap(Collection::stream)
                .forEach(a -> a
                        .isTargetFormat(FILE_FORMAT_PATTERN.matcher(a.originalData().filename()).find())
                        // Almost all audio files will be at least 500kb and this helps
                        // filter out e.g. tiny metadata files that contain the song name
                        .sizeOk(a.originalData().size() > 500_000)
                );

        return ProcessorUserResultBuilder.builder()
                .username(resp.username())
                .byTrackName(matches);
    }

    private static final int MATCHED_TRACKS_POINTS = 50;
    private static final int AVERAGE_SCORE_POINTS = 30;
    private static final int AVAILABLE_USER_POINTS = MATCHED_TRACKS_POINTS + AVERAGE_SCORE_POINTS;

    private ProcessorUserResultBuilder scoreUser(ProcessorUserResultBuilder builder, AlbumInfo albumInfo) {

        final double percentTracksMatched = (float) builder.bestCandidates().size() / albumInfo.tracks().size();
        final double averageScore = builder.bestCandidates().stream()
                        .mapToDouble(ProcessorFileResult::score)
                        .sum() / builder.bestCandidates().size();

        final double finalScore = (MATCHED_TRACKS_POINTS * percentTracksMatched + AVERAGE_SCORE_POINTS * averageScore)
                / AVAILABLE_USER_POINTS;
        builder.scoreOfBestCandidates((float) finalScore);

        return builder;
    }

    private ProcessorUserResultBuilder computeBestFiles(ProcessorUserResultBuilder builder) {

        final List<ProcessorFileResult> prfs = builder.byTrackName().values().stream()
                // Score the matches and return any one of the highest scoring matches
                .map(pfrb -> pfrb.stream().map(this::scoreMatches).max(Comparator.comparing(ProcessorFileResultBuilder::score)))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(ProcessorFileResultBuilder::build)
                .toList();

        builder.bestCandidates(prfs);

        return builder;
    }

    private static final int SIZE_POINTS = 20;
    private static final int FORMAT_POINTS = 50;
    private static final int AVAILABLE_FILE_POINTS = SIZE_POINTS + FORMAT_POINTS;

    @CanIgnoreReturnValue
    private ProcessorFileResultBuilder scoreMatches(ProcessorFileResultBuilder builder) {
        
        final int sizePoints = builder.sizeOk() ? SIZE_POINTS : 0;
        final int formatPoints = builder.isTargetFormat() ? FORMAT_POINTS : 0;

        builder.score((float) (sizePoints + formatPoints) / AVAILABLE_FILE_POINTS);
        return builder;
    }
}
