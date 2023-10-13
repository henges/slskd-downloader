package dev.polluxus.spotify_offline_playlist.processor.model;

import dev.polluxus.spotify_offline_playlist.model.AlbumInfo;
import io.soabase.recordbuilder.core.RecordBuilder;

import java.util.List;

@RecordBuilder
public record ProcessorSearchResult(
        AlbumInfo albumInfo,
        List<ProcessorUserResult> userResults
) {

}
