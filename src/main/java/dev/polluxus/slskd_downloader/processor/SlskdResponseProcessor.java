package dev.polluxus.slskd_downloader.processor;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import dev.polluxus.slskd_downloader.client.slskd.response.SlskdSearchDetailResponse;
import dev.polluxus.slskd_downloader.model.AlbumInfo;
import dev.polluxus.slskd_downloader.model.AlbumInfo.AlbumTrack;
import dev.polluxus.slskd_downloader.processor.matcher.MatchStrategyType;
import dev.polluxus.slskd_downloader.processor.model.input.ProcessorInputUser;
import dev.polluxus.slskd_downloader.processor.model.output.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
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
                .filter(r -> !r.directories().isEmpty())
                .map(r -> computeBestDirectories(r, albumInfo))
                .map(r -> scoreUser(r, albumInfo))
                .map(ProcessorUserResultBuilder::build)
                .filter(ur -> !ur.directories().isEmpty())
                .sorted(Comparator.comparing(ProcessorUserResult::scoreOfBestCandidates).reversed())
                .toList());
    }

    private ProcessorUserResultBuilder findMatches(ProcessorInputUser resp, AlbumInfo albumInfo) {

        final List<ProcessorDirectoryResultBuilder> directoryResults = resp.directories().stream()
                .map(d -> {
                    final Map<String, List<ProcessorFileResultBuilder>> matches = matchStrategy.match(d, albumInfo);
                    matches.values().stream().flatMap(Collection::stream)
                            .forEach(a -> a
                                    .isTargetFormat(FILE_FORMAT_PATTERN.matcher(a.originalData().filename()).find())
                                    // Almost all audio files will be at least 500kb and this helps
                                    // filter out e.g. tiny metadata files that contain the song name
                                    .sizeOk(a.originalData().size() > 500_000)
                            );
                    return ProcessorDirectoryResultBuilder.builder()
                            .byTrackName(matches);
                })
                .filter(d -> !d.byTrackName().isEmpty())
                .toList();

        return ProcessorUserResultBuilder.builder()
                .username(resp.originalData().username())
                .directories(directoryResults);
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
        builder.scoreOfBestCandidates(finalScore);

        return builder;
    }

    private ProcessorUserResultBuilder computeBestFilesOriginal(ProcessorUserResultBuilder builder) {

//        final List<List<ProcessorFileResult>> prfs = builder.byTrackName().values().stream()
//                // Score the matches and return any one of the highest scoring matches
//                .map(pfrb -> pfrb.stream().map(this::scoreMatches)
//                        .map(ProcessorFileResultBuilder::build)
//                        .sorted(Comparator.comparing(ProcessorFileResult::score, Comparator.reverseOrder()))
//                        .toList())
//                .toList();
//        final Map<String, ProcessorFileResult> uniques = new HashMap<>();
//        for (var res : prfs) {
//            for (var entry : res) {
//                final String key = entry.originalData().filename();
//                // Skip entries that matched more than one name
//                if (uniques.containsKey(key)) {
//                    continue;
//                }
//                uniques.put(key, entry);
//                break;
//            }
//        }

//        builder.bestCandidates(uniques.values().stream().toList());

        return builder;
    }

    private ProcessorUserResultBuilder computeBestDirectories(ProcessorUserResultBuilder builder, AlbumInfo albumInfo) {

        // Oh my god, I have no idea if this is correct.
        builder.directories().forEach(d -> {
            // Find the best candidates from each directory
            // Compute the overall score of the directory - the number of candidate tracks matched.
            final Map<String, ProcessorFileResult> uniques = new HashMap<>();
            d.byTrackName().forEach((numberAndTitle, candidates) -> {
                // Sort the candidates by score
                final List<ProcessorFileResult> bestCandidatesForTrack = candidates.stream()
                        .map(this::scoreMatches)
                        .map(ProcessorFileResultBuilder::build)
                        .sorted(Comparator.comparing(ProcessorFileResult::score).reversed())
                        .toList();
                for (var candi : bestCandidatesForTrack) {
                    final String filename = candi.originalData().filename();
                    // Skip entries that matched more than one name
                    if (uniques.containsKey(filename)) {
                        continue;
                    }
                    uniques.put(filename, candi);
                    break;
                }
            });
            d.bestCandidates(uniques.values().stream().toList());
            d.score((double) d.bestCandidates().size() / albumInfo.tracks().size());
        });
        // Now compute the final list of tracks to be selected from this user.
        // We continue until all track names have at least one match or until the list of
        // good candidates is exhausted.
        final List<ProcessorDirectoryResultBuilder> sorted = builder.directories().stream()
                .sorted(Comparator.comparing((ProcessorDirectoryResultBuilder b) -> b.score()).reversed())
                .toList();
        final Set<String> tracksToMatch = albumInfo.tracks().stream().map(AlbumTrack::numberAndTitle).collect(Collectors.toSet());
        final List<ProcessorFileResult> finalMatches = new ArrayList<>();
        for (var drb : sorted) {
            for (var trk : drb.bestCandidates()) {
                // TODO: what if it matched multiple tracks? we might end up missing a good match because of that.
                var albumTrk = new AlbumTrack(trk.matchDetails().matchesNumber(), trk.matchDetails().matchesTitle());
                if (tracksToMatch.contains(albumTrk.numberAndTitle())) {
                    finalMatches.add(trk);
                    tracksToMatch.remove(albumTrk.numberAndTitle());
                }
            }
        }
        return builder.bestCandidates(finalMatches);
    }

    private static final int SIZE_POINTS = 20;
    private static final int FORMAT_POINTS = 20;
    private static final int DISTANCE_POINTS = 50;
    private static final int AVAILABLE_FILE_POINTS = SIZE_POINTS + FORMAT_POINTS + DISTANCE_POINTS;

    @CanIgnoreReturnValue
    private ProcessorFileResultBuilder scoreMatches(ProcessorFileResultBuilder builder) {
        
        final int sizePoints = builder.sizeOk() ? SIZE_POINTS : 0;
        final int formatPoints = builder.isTargetFormat() ? FORMAT_POINTS : 0;
        // 1. Add 1 to the value so that log of distance 0 = 0 (log10(1) == 0.0)
        // 2. Take the smaller of the log and 1, so that negative points aren't awarded
        //     if the result is >1
        // 3. Subtract this value from 1 to find a scaling factor for the distance points
        // 4. Scale the distance points
        final double distancePoints = DISTANCE_POINTS *
                (1 - Math.min(Math.log10(builder.matchDetails().distance() + 1), 1));

        builder.score((float) (sizePoints + formatPoints + distancePoints) / AVAILABLE_FILE_POINTS);
        return builder;
    }
}
