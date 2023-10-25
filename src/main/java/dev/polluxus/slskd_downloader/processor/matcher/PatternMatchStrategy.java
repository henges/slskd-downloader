package dev.polluxus.slskd_downloader.processor.matcher;

import dev.polluxus.slskd_downloader.model.AlbumInfo;
import dev.polluxus.slskd_downloader.model.AlbumInfo.AlbumTrack;
import dev.polluxus.slskd_downloader.processor.model.output.ProcessorFileResultBuilder;
import dev.polluxus.slskd_downloader.processor.model.input.ProcessorInputUser.ProcessorInputDirectory;
import dev.polluxus.slskd_downloader.processor.model.output.ProcessorMatchDetailsBuilder;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PatternMatchStrategy implements MatchStrategy {

    private final ConcurrentMap<String, Map<Pattern, AlbumTrack>> patternCache = new ConcurrentHashMap<>();

    @Override
    public Map<String, List<ProcessorFileResultBuilder>> apply(ProcessorInputDirectory resp, AlbumInfo albumInfo) {

        final Map<Pattern, AlbumTrack> patterns = patternCache.computeIfAbsent(albumInfo.searchString(), (k) -> {
            return albumInfo.tracks().stream()
                    .collect(Collectors.toMap(
                            n -> Pattern.compile(n.title(), Pattern.CASE_INSENSITIVE),
                            n -> n
                    ));
        });

        // Iterate through all the responses and record the first one that matches
        // Generally the number of files in a given response will be as large as
        // or larger than the number of tracks in the request, so this is a good
        // choice, but you could also iterate according to whichever one was smaller.
        Map<String, List<ProcessorFileResultBuilder>> matchesForPattern = new HashMap<>(patterns.size());
        for (var currentResult : resp.files()) {
            for (Pattern currentTarget : patterns.keySet()) {
                if (currentTarget.matcher(currentResult.filename()).find()) {

                    var pr = ProcessorFileResultBuilder.builder()
                            .originalData(currentResult.originalData())
                            .matchDetails(ProcessorMatchDetailsBuilder.builder()
                                    .matchesTitle(currentTarget.toString())
                                    .matchesNumber(patterns.get(currentTarget).number())
                                    .build());
                    matchesForPattern.computeIfAbsent(patterns.get(currentTarget).numberAndTitle(), (k) -> new ArrayList<>()).add(pr);
                }
            }
        }

        return matchesForPattern;
    }
}
