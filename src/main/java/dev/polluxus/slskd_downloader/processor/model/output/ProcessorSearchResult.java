package dev.polluxus.slskd_downloader.processor.model.output;

import dev.polluxus.slskd_downloader.config.Builder;
import dev.polluxus.slskd_downloader.model.AlbumInfo;

import java.util.List;

@Builder
public record ProcessorSearchResult(
        AlbumInfo albumInfo,
        List<ProcessorUserResult> userResults
) {

}
