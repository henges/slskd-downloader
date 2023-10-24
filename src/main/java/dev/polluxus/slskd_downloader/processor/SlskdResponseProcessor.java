package dev.polluxus.slskd_downloader.processor;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import dev.polluxus.slskd_downloader.client.slskd.response.SlskdSearchDetailResponse;
import dev.polluxus.slskd_downloader.model.AlbumInfo;
import dev.polluxus.slskd_downloader.processor.matcher.MatchStrategyType;
import dev.polluxus.slskd_downloader.processor.model.output.*;
import dev.polluxus.slskd_downloader.processor.model.input.ProcessorInputUser;
import dev.polluxus.slskd_downloader.processor.model.output.ProcessorDirectoryResult;
import dev.polluxus.slskd_downloader.processor.model.output.ProcessorFileResult;
import dev.polluxus.slskd_downloader.processor.model.output.ProcessorSearchResult;
import dev.polluxus.slskd_downloader.processor.model.output.ProcessorUserResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import static dev.polluxus.slskd_downloader.processor.matcher.MatchStrategy.FILE_FORMAT_PATTERN;

public class SlskdResponseProcessor {

    private static final Logger log = LoggerFactory.getLogger(SlskdResponseProcessor.class);

    private final MatchStrategyType matchStrategy;

    public SlskdResponseProcessor(MatchStrategyType matchStrategy) {
        this.matchStrategy = matchStrategy;
    }

    public ProcessorSearchResult process(List<SlskdSearchDetailResponse> resps, AlbumInfo albumInfo) {

        return new ProcessorSearchResult(albumInfo, resps.stream()
                .map(ProcessorInputUser::convert)
                .map(r -> findMatches(r, albumInfo))
                .map(this::computeBestFiles)
                .map(r -> scoreUser(r, albumInfo))
                .map(ProcessorUserResultBuilder::build)
                .filter(ur -> !ur.byTrackName().isEmpty())
                .sorted(Comparator.comparing(ProcessorUserResult::scoreOfBestCandidates).reversed())
                .toList());
    }

    private ProcessorUserResultBuilder findMatches(ProcessorInputUser resp, AlbumInfo albumInfo) {

        final List<ProcessorDirectoryResult> directoryResults = resp.directories().stream()
                .map(d -> {
                    final Map<String, List<ProcessorFileResultBuilder>> matches = matchStrategy.match(d, albumInfo);
                    matches.values().stream().flatMap(Collection::stream)
                            .forEach(a -> a
                                    .isTargetFormat(FILE_FORMAT_PATTERN.matcher(a.originalData().filename()).find())
                                    // Almost all audio files will be at least 500kb and this helps
                                    // filter out e.g. tiny metadata files that contain the song name
                                    .sizeOk(a.originalData().size() > 500_000)
                            );
                    return new ProcessorDirectoryResult(matches);
                })
                .toList();

        return ProcessorUserResultBuilder.builder()
                .username(resp.originalData().username())
                .byTrackName(directoryResults.stream()
                        .flatMap(d -> d.byTrackName().entrySet().stream())
                        .collect(Collectors.toMap(
                                Entry::getKey,
                                Entry::getValue,
                                (o1, o2) -> {
                                    o1.addAll(o2);
                                    return o1;
                                }
                        )));
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

        // TODO here: use directories in scoring, give a higher score to a directory
        //  based on how many tracks it matches, select results by-directory
        final List<List<ProcessorFileResult>> prfs = builder.byTrackName().values().stream()
                // Score the matches and return any one of the highest scoring matches
                .map(pfrb -> pfrb.stream().map(this::scoreMatches)
                        .map(ProcessorFileResultBuilder::build)
                        .sorted(Comparator.comparing(ProcessorFileResult::score, Comparator.reverseOrder()))
                        .toList())
                .toList();
        final Map<String, ProcessorFileResult> uniques = new HashMap<>();
        for (var res : prfs) {
            for (var entry : res) {
                final String key = entry.originalData().filename();
                // Skip entries that matched more than one name
                if (uniques.containsKey(key)) {
                    continue;
                }
                uniques.put(key, entry);
                break;
            }
        }

        builder.bestCandidates(uniques.values().stream().toList());

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
