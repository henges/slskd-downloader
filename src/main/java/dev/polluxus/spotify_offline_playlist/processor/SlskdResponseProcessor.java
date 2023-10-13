package dev.polluxus.spotify_offline_playlist.processor;

import dev.polluxus.spotify_offline_playlist.client.slskd.response.SlskdSearchDetailResponse;
import dev.polluxus.spotify_offline_playlist.client.slskd.response.SlskdSearchDetailResponse.SlskdSearchMatchResponse;
import dev.polluxus.spotify_offline_playlist.model.AlbumInfo;
import dev.polluxus.spotify_offline_playlist.processor.matcher.MatchStrategyType;
import dev.polluxus.spotify_offline_playlist.processor.model.ProcessorFileResult;
import dev.polluxus.spotify_offline_playlist.processor.model.ProcessorSearchResult;
import dev.polluxus.spotify_offline_playlist.processor.model.ProcessorUserResultBuilder;
import dev.polluxus.spotify_offline_playlist.util.TriFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

public class SlskdResponseProcessor {

    private static final Logger log = LoggerFactory.getLogger(SlskdResponseProcessor.class);

    public static ProcessorSearchResult process(List<SlskdSearchDetailResponse> resps, AlbumInfo albumInfo) {

        return new ProcessorSearchResult(albumInfo, resps.stream()
                .map(r -> findMatches(r, albumInfo).build())
                .toList());
    }

    private static ProcessorUserResultBuilder findMatches(SlskdSearchDetailResponse resp, AlbumInfo albumInfo) {

        Map<String, List<ProcessorFileResult>> matches = MatchStrategyType.PATTERN_MATCH.match(resp, albumInfo);

        final List<ProcessorFileResult> matchingFiles = matches.values().stream()
                .flatMap(Collection::stream)
                .toList();
        final float matchedPercent = (float) matches.keySet().size() / albumInfo.tracks().size();

        return ProcessorUserResultBuilder.builder()
                .username(resp.username())
                .files(matchingFiles);
    }

    @Deprecated
    public static class Heuristics {

        public static int score(SlskdSearchDetailResponse result, List<String> targetTrackNames) {

            boolean hasRightFormat = Heuristics.hasRightFormatAndLength.apply(result, "flac", targetTrackNames.size());
            boolean hasRightBitrate = Heuristics.hasRightBitrate.apply(result, 224, "GE");
            boolean hasAllTracks = Heuristics.hasAllTracks.apply(result, targetTrackNames);

            return (hasAllTracks ? 5 : 0) + (hasRightFormat ? 3 : 0)
                    + (hasRightBitrate ? 1 : 0)
                    ;
        }

        static TriFunction<SlskdSearchDetailResponse, String, Integer, Boolean> hasRightFormatAndLength = (res, fmt, len) -> {

            Pattern fmtPattern = Pattern.compile("\\." + fmt, Pattern.CASE_INSENSITIVE);
            return res.files().stream()
                    .filter(t -> fmtPattern.matcher(t.filename()).find())
                    .toList().size() >= len;
        };

        static TriFunction<SlskdSearchDetailResponse, Integer, String, Boolean> hasRightBitrate = (res, bitrate, op) -> {

            List <Integer> bitrates = res.files().stream()
                    .filter(f -> f.bitRate().isPresent())
                    .map(f -> f.bitRate().get()).toList();
            if (bitrates.isEmpty()) {
                return false;
            }
            return bitrates.stream().mapToInt(i -> i).sum() / bitrates.size() >= bitrate;
        };

        static BiFunction<SlskdSearchDetailResponse, List<String>, Boolean> hasAllTracks = (res, tgt) -> {

            final List<Pattern> patterns = tgt.stream()
                    .map(n -> Pattern.compile(n, Pattern.CASE_INSENSITIVE))
                    .toList();

            return res.files().stream().map(SlskdSearchMatchResponse::filename)
                    .allMatch(track -> patterns.stream()
                            .anyMatch(p -> p.matcher(track).find()));
        };

    }
}
