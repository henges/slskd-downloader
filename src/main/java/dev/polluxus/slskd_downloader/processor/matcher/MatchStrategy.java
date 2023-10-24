package dev.polluxus.slskd_downloader.processor.matcher;

import dev.polluxus.slskd_downloader.model.AlbumInfo;
import dev.polluxus.slskd_downloader.processor.model.output.ProcessorFileResultBuilder;
import dev.polluxus.slskd_downloader.processor.model.input.ProcessorInputUser.ProcessorInputDirectory;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

public interface MatchStrategy extends BiFunction<ProcessorInputDirectory, AlbumInfo, Map<String, List<ProcessorFileResultBuilder>>> {

    String TARGET_FORMAT = "flac";
    Pattern FILE_FORMAT_PATTERN = Pattern.compile("\\." + TARGET_FORMAT, Pattern.CASE_INSENSITIVE);
}
