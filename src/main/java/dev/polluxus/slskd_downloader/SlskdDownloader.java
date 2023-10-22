package dev.polluxus.slskd_downloader;

import au.com.muel.envconfig.EnvConfig;
import dev.polluxus.slskd_downloader.config.Config;
import dev.polluxus.slskd_downloader.decisionmaker.UnattendedDecisionMaker;
import dev.polluxus.slskd_downloader.infosupplier.AlbumInfoSupplier;
import dev.polluxus.slskd_downloader.model.AlbumInfo;
import dev.polluxus.slskd_downloader.processor.DownloadProcessor;
import dev.polluxus.slskd_downloader.processor.DownloadProcessor.DownloadResult;
import dev.polluxus.slskd_downloader.processor.SlskdResponseProcessor;
import dev.polluxus.slskd_downloader.processor.matcher.MatchStrategyType;
import dev.polluxus.slskd_downloader.processor.model.ProcessorSearchResult;
import dev.polluxus.slskd_downloader.service.SlskdService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SlskdDownloader {

    private static final Logger log = LoggerFactory.getLogger(SlskdDownloader.class);

    public static void main(String[] args) {

        final Config config = EnvConfig.fromEnv(Config.class);

        process(config);
    }

    public static void process(final Config config) {

        final SlskdService slskdService = new SlskdService(config);
        final Iterator<AlbumInfo> supplier = AlbumInfoSupplier.from(config);

        final SlskdResponseProcessor processor = new SlskdResponseProcessor(MatchStrategyType.EDIT_DISTANCE);
        final DownloadProcessor downloadProcessor = new DownloadProcessor(slskdService, new UnattendedDecisionMaker());

        Map<AlbumInfo, CompletableFuture<DownloadResult>> results = pipeline(supplier, slskdService, processor, downloadProcessor);
        postComplete(results);

        downloadProcessor.stop();
        slskdService.shutdown();
    }

    public static Map<AlbumInfo, CompletableFuture<DownloadResult>> pipeline(Iterator<AlbumInfo> albumInfos,
                                SlskdService service,
                                SlskdResponseProcessor processor,
                                Function<ProcessorSearchResult, CompletableFuture<DownloadResult>> consumer) {

        final Map<AlbumInfo, CompletableFuture<DownloadResult>> allRequests = new HashMap<>();
        while (albumInfos.hasNext()) {
            final AlbumInfo ai = albumInfos.next();
            final var doneFuture = service.search(ai)
                    .thenApply(l -> processor.process(l, ai))
                    .thenCompose(consumer);
            allRequests.put(ai, doneFuture);
        }
        CompletableFuture.allOf(allRequests.values().toArray(CompletableFuture[]::new)).join();
        log.info("All requests done.");
        return allRequests;
    }

    public static void postComplete(Map<AlbumInfo, CompletableFuture<DownloadResult>> results) {
        Map<DownloadResult, List<AlbumInfo>> failures = results.entrySet().stream()
                .filter(e -> !e.getValue().join().equals(DownloadResult.OK))
                .collect(Collectors.toMap(
                        e -> e.getValue().join(),
                        e -> {
                            List<AlbumInfo> r = new ArrayList<>();
                            r.add(e.getKey());
                            return r;
                        },
                        (e1, e2) -> {
                            e1.addAll(e2);
                            return e1;
                        }
                ));
        final List<AlbumInfo> triedAndFailed = failures.getOrDefault(DownloadResult.TRIED_FAILED, List.of());
        final List<AlbumInfo> didntTry = failures.getOrDefault(DownloadResult.DIDNT_TRY, List.of());
        final List<AlbumInfo> explicitlySkipped = failures.getOrDefault(DownloadResult.EXPLICITLY_SKIPPED, List.of());
        if (!triedAndFailed.isEmpty()) {
            log.info("The following searches were attempted, but failed to download correctly:");
            triedAndFailed.forEach(ai -> log.info("\t{}", ai.searchString()));
        }
        if (!didntTry.isEmpty()) {
            log.info("The following searches either had no results or had all results rejected:");
            didntTry.forEach(ai -> log.info("\t{}", ai.searchString()));
        }
        if (!explicitlySkipped.isEmpty()) {
            log.info("The following searches were explicitly skipped:");
            explicitlySkipped.forEach(ai -> log.info("\t{}", ai.searchString()));
        }
    }
}
