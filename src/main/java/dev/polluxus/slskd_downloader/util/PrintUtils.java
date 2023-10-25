package dev.polluxus.slskd_downloader.util;

import dev.polluxus.slskd_downloader.processor.model.output.ProcessorUserResult;

import java.util.Comparator;

public class PrintUtils {

    public static String printProcessorUserResult(ProcessorUserResult r) {

        final StringBuilder sb = new StringBuilder();

        for (var f : r.bestCandidates().stream().sorted(Comparator.comparing(x -> x.originalData().filename())).toList()) {
            sb.append(String.format("\t%s\n", f.originalData().filename()));
        }

        return sb.toString();
    }
}
