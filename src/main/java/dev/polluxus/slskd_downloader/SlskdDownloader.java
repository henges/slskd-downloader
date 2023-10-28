package dev.polluxus.slskd_downloader;

import au.com.muel.envconfig.EnvConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import dev.polluxus.slskd_downloader.client.slskd.response.SlskdSearchDetailResponse;
import dev.polluxus.slskd_downloader.config.Config;
import dev.polluxus.slskd_downloader.decisionmaker.UnattendedDecisionMaker;
import dev.polluxus.slskd_downloader.infosupplier.AlbumInfoSupplier;
import dev.polluxus.slskd_downloader.model.AlbumInfo;
import dev.polluxus.slskd_downloader.processor.DownloadProcessor;
import dev.polluxus.slskd_downloader.processor.DownloadProcessor.DownloadResult;
import dev.polluxus.slskd_downloader.processor.SlskdResponseProcessor;
import dev.polluxus.slskd_downloader.processor.matcher.MatchStrategyType;
import dev.polluxus.slskd_downloader.processor.model.output.ProcessorSearchResult;
import dev.polluxus.slskd_downloader.service.SlskdService;
import dev.polluxus.slskd_downloader.store.FileBackedStore;
import dev.polluxus.slskd_downloader.store.Store;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

public class SlskdDownloader {

    private static final Logger log = LoggerFactory.getLogger(SlskdDownloader.class);

    public static void main(String[] args) {

        final Config config = EnvConfig.fromEnv(Config.class);

        process(config);
    }

    public static void process(final Config config) {

        final SlskdService slskdService = new SlskdService(config).start();
        final Iterator<AlbumInfo> supplier = AlbumInfoSupplier.from(config);

        final SlskdResponseProcessor processor = SlskdResponseProcessor.from(config, MatchStrategyType.EDIT_DISTANCE);
        final DownloadProcessor downloadProcessor = new DownloadProcessor(slskdService, new UnattendedDecisionMaker());

        Map<AlbumInfo, CompletableFuture<DownloadResult>> results = pipeline(
                supplier,
                slskdService,
                processor,
                downloadProcessor,
                (ai) -> ai.artists().stream().anyMatch(a -> a.equalsIgnoreCase("Iury Lech")));
        postComplete(results);

        downloadProcessor.stop();
        slskdService.shutdown();
    }

    public static Map<AlbumInfo, CompletableFuture<DownloadResult>> pipeline(
                                Iterator<AlbumInfo> albumInfos,
                                SlskdService service,
                                SlskdResponseProcessor processor,
                                Function<ProcessorSearchResult, CompletableFuture<DownloadResult>> consumer,
                                Predicate<AlbumInfo> skipUntil) {

        final Map<AlbumInfo, CompletableFuture<DownloadResult>> allRequests = new HashMap<>();
        boolean skipping = true;
        while (albumInfos.hasNext()) {
            final AlbumInfo ai = albumInfos.next();
            // Skip until the predicate returns true
            if (skipping) {
                skipping = !skipUntil.test(ai);
                if (skipping) {
                    log.info(STR."Skipping entry \{ai.searchString()} because it doesn't match the predicate");
                    continue;
                }
            }
            final var doneFuture = service.search(ai)
                    .thenApply(l -> processor.process(l, ai))
                    .thenCompose(consumer)
                    .exceptionally(t -> {
                        log.error("Handling exception in pipeline not caught by more specific handler", t);
                        return DownloadResult.TRIED_FAILED;
                    });
            allRequests.put(ai, doneFuture);
        }
        CompletableFuture.allOf(allRequests.values().toArray(CompletableFuture[]::new)).join();
        log.info("All requests done.");
        return allRequests;
    }

    public static void postComplete(Map<AlbumInfo, CompletableFuture<DownloadResult>> results) {
        Map<DownloadResult, List<AlbumInfo>> failures = results.entrySet().stream()
                .collect(Collectors.groupingBy(e -> e.getValue().join(), mapping(Entry::getKey, toList())));

        final List<AlbumInfo> ok = failures.getOrDefault(DownloadResult.OK, List.of());
        final List<AlbumInfo> triedAndFailed = failures.getOrDefault(DownloadResult.TRIED_FAILED, List.of());
        final List<AlbumInfo> didntTry = failures.getOrDefault(DownloadResult.DIDNT_TRY, List.of());
        final List<AlbumInfo> explicitlySkipped = failures.getOrDefault(DownloadResult.EXPLICITLY_SKIPPED, List.of());
        if (!ok.isEmpty()) {
            log.info("The following downloads were submitted OK:");
            ok.forEach(ai -> log.info("\t{}", ai.searchString()));
        }
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
