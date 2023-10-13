package dev.polluxus.spotify_offline_playlist.processor;

import dev.polluxus.spotify_offline_playlist.client.slskd.response.SlskdSearchDetailResponse;
import dev.polluxus.spotify_offline_playlist.model.AlbumInfo;
import dev.polluxus.spotify_offline_playlist.processor.matcher.MatchStrategyType;
import dev.polluxus.spotify_offline_playlist.processor.model.ProcessorFileResult;
import dev.polluxus.spotify_offline_playlist.processor.model.ProcessorFileResultBuilder;
import dev.polluxus.spotify_offline_playlist.processor.model.ProcessorSearchResult;
import dev.polluxus.spotify_offline_playlist.processor.model.ProcessorUserResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;

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
                .map(ProcessorUserResultBuilder::build)
                .filter(ur -> !ur.byTrackName().isEmpty())
                .toList());
    }

    private ProcessorUserResultBuilder findMatches(SlskdSearchDetailResponse resp, AlbumInfo albumInfo) {

        final Map<String, List<ProcessorFileResultBuilder>> matches = MatchStrategyType.EDIT_DISTANCE.match(resp, albumInfo);
        matches.values().stream().flatMap(Collection::stream)
                .forEach(a -> a
                        .isTargetFormat(FILE_FORMAT_PATTERN.matcher(a.originalData().filename()).find())
                        // Almost all audio files will be at least 500kb and this helps
                        // filter out e.g. tiny metadata files that contain the song name
                        .sizeOk(a.originalData().size() > 500_000)
                );

//        final float matchedPercent = (float) matches.keySet().size() / albumInfo.tracks().size();

        return ProcessorUserResultBuilder.builder()
                .username(resp.username())
                .byTrackName(matches);
    }

    private ProcessorUserResultBuilder computeBestFiles(ProcessorUserResultBuilder builder) {

        for (var e : builder.byTrackName().entrySet()) {
            
            e.getValue().stream().map(f -> {
                scoreMatches(f);

                
            });
        }
        

        return builder;
    }

    private static final int SIZE_POINTS = 20;
    private static final int FORMAT_POINTS = 50;
    private static final int AVAILABLE_POINTS = SIZE_POINTS + FORMAT_POINTS;

    private void scoreMatches(ProcessorFileResultBuilder builder) {
        
        final int sizePoints = builder.sizeOk() ? SIZE_POINTS : 0;
        final int formatPoints = builder.isTargetFormat() ? FORMAT_POINTS : 0;

        builder.score((float) (sizePoints + formatPoints) / AVAILABLE_POINTS);
    }
}
