package dev.polluxus.spotify_offline_playlist.processor.matcher;

import dev.polluxus.spotify_offline_playlist.client.slskd.response.SlskdSearchDetailResponse;
import dev.polluxus.spotify_offline_playlist.client.slskd.response.SlskdSearchDetailResponse.SlskdSearchMatchResponse;
import dev.polluxus.spotify_offline_playlist.model.AlbumInfo;
import dev.polluxus.spotify_offline_playlist.processor.model.ProcessorFileResult;
import dev.polluxus.spotify_offline_playlist.processor.model.ProcessorFileResultBuilder;
import dev.polluxus.spotify_offline_playlist.processor.model.ProcessorMatchDetailsBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class PatternMatchStrategy implements MatchStrategy {

    @Override
    public Map<String, List<ProcessorFileResultBuilder>> apply(SlskdSearchDetailResponse resp, AlbumInfo albumInfo) {

        // TODO: We need to distance match here. Otherwise we will miss tracks
        //  because of small typos in track names (on either sender or receiver side).
        final List<Pattern> patterns = albumInfo.tracks().stream()
                .map(n -> Pattern.compile(n.title(), Pattern.CASE_INSENSITIVE))
                .toList();

        // Iterate through all the responses and record the first one that matches
        // Generally the number of files in a given response will be as large as
        // or larger than the number of tracks in the request, so this is a good
        // choice, but you could also iterate according to whichever one was smaller.
        Map<String, List<ProcessorFileResultBuilder>> matchesForPattern = new HashMap<>(patterns.size());
        for (SlskdSearchMatchResponse currentResult : resp.files()) {
            for (Pattern currentTarget : patterns) {
                if (currentTarget.matcher(currentResult.filename()).find()) {

                    var pr = ProcessorFileResultBuilder.builder()
                            .originalData(currentResult)
                            .matchDetails(ProcessorMatchDetailsBuilder.builder()
                                    .matchesTitle(currentTarget.toString())
                                    .build());
                    matchesForPattern.computeIfAbsent(currentTarget.toString(), (k) -> new ArrayList<>()).add(pr);
                }
            }
        }

        return matchesForPattern;
    }
}
