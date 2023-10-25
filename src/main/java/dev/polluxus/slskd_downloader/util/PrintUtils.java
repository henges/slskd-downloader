package dev.polluxus.slskd_downloader.util;

import dev.polluxus.slskd_downloader.model.AlbumInfo;
import dev.polluxus.slskd_downloader.processor.model.output.ProcessorUserResult;

import java.util.Comparator;

public class PrintUtils {

    public static String printProcessorUserResult(ProcessorUserResult r, AlbumInfo ai) {

        final StringBuilder sb = new StringBuilder();

        for (var f : r.bestCandidates().stream().sorted(Comparator.comparing(x -> x.originalData().filename())).toList()) {
            sb.append(String.format("\t%s\n", f.originalData().filename()));
        }
        sb.append(STR."\{r.bestCandidates().size()} tracks total (\{r.bestCandidates().size() == ai.tracks().size() ? "correct number" : "NOT CORRECT number"})\n");

        return sb.toString();
    }
}
