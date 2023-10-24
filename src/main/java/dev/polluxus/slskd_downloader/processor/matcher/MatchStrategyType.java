package dev.polluxus.slskd_downloader.processor.matcher;

import dev.polluxus.slskd_downloader.model.AlbumInfo;
import dev.polluxus.slskd_downloader.processor.model.output.ProcessorFileResultBuilder;
import dev.polluxus.slskd_downloader.processor.model.input.ProcessorInputUser.ProcessorInputDirectory;

import java.util.List;
import java.util.Map;

public enum MatchStrategyType {
    PATTERN_MATCH(new PatternMatchStrategy()),
    EDIT_DISTANCE(new EditDistanceStrategy());

    private final MatchStrategy func;

    MatchStrategyType(MatchStrategy func) {
        this.func = func;
    }

    public Map<String, List<ProcessorFileResultBuilder>> match(ProcessorInputDirectory resp, AlbumInfo albumInfo) {
        return this.func.apply(resp, albumInfo);
    }
}
