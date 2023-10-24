package dev.polluxus.slskd_downloader.processor.model.input;

import dev.polluxus.slskd_downloader.client.slskd.response.SlskdSearchDetailResponse;
import dev.polluxus.slskd_downloader.client.slskd.response.SlskdSearchDetailResponse.SlskdSearchMatchResponse;
import dev.polluxus.slskd_downloader.util.FilenameUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.mapping;

// TODO ARJ: better name!
public record ProcessorInputUser(
        SlskdSearchDetailResponse originalData,
        List<ProcessorInputDirectory> directories
) {

    public record ProcessorInputDirectory(
            String parentDirName,
            List<ProcessorInputFile> files
    ) {}

    public record ProcessorInputFile(
            SlskdSearchMatchResponse originalData,
            String filename
    ) {}

    public static ProcessorInputUser convert(SlskdSearchDetailResponse r) {

        final var userDirectories = r.files().stream()
                .map(m -> Pair.of(m, FilenameUtils.getParentName(m.filename())))
                .collect(Collectors.groupingBy(Pair::getValue, mapping(Pair::getKey, Collectors.toList())))
                .entrySet().stream()
                .map(e -> new ProcessorInputDirectory(e.getKey(), e.getValue().stream()
                        .map(m -> new ProcessorInputFile(m, m.filename())).toList()))
                .toList();
        return new ProcessorInputUser(r, userDirectories);
    }
}
