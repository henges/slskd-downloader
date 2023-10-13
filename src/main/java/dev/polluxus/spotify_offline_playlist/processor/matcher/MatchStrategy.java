package dev.polluxus.spotify_offline_playlist.processor.matcher;

import dev.polluxus.spotify_offline_playlist.client.slskd.response.SlskdSearchDetailResponse;
import dev.polluxus.spotify_offline_playlist.model.AlbumInfo;
import dev.polluxus.spotify_offline_playlist.processor.model.ProcessorFileResult;
import org.apache.commons.text.similarity.LevenshteinDistance;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

public interface MatchStrategy extends BiFunction<SlskdSearchDetailResponse, AlbumInfo, Map<String, List<ProcessorFileResult>>> {

    String TARGET_FORMAT = "flac";
    Pattern FILE_FORMAT_PATTERN = Pattern.compile("\\." + TARGET_FORMAT, Pattern.CASE_INSENSITIVE);
}
