package dev.polluxus.slskd_downloader;

import au.com.muel.envconfig.EnvConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import dev.polluxus.slskd_downloader.client.slskd.response.SlskdSearchDetailResponse;
import dev.polluxus.slskd_downloader.config.Config;
import dev.polluxus.slskd_downloader.infosupplier.AlbumInfoSupplier;
import dev.polluxus.slskd_downloader.model.AlbumInfo;
import dev.polluxus.slskd_downloader.service.SlskdService;
import dev.polluxus.slskd_downloader.store.FileBackedStore;
import dev.polluxus.slskd_downloader.store.Store;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;

public class PullTestData {

    public static void main(String[] args) {

        final Config config = EnvConfig.fromEnv(Config.class);

        final SlskdService slskdService = new SlskdService(config).start();
        final Iterator<AlbumInfo> supplier = AlbumInfoSupplier.from(config);
        final Store<AlbumInfo> albumStore = FileBackedStore.from(Path.of(
                "C:\\Users\\alexa\\code\\slskd-downloader\\src\\test\\resources\\requests"), AlbumInfo.class);
        final Store<List<SlskdSearchDetailResponse>> resultStore = FileBackedStore.from(Path.of(
                "C:\\Users\\alexa\\code\\slskd-downloader\\src\\test\\resources\\responses"), new TypeReference<>() {});
        for (int i = 0; i < 400 && supplier.hasNext(); i++) {

            AlbumInfo curr = supplier.next();
            List<SlskdSearchDetailResponse> res = slskdService.search(curr).join();
            final String name = STR."\{String.join("+", curr.artists())
                    .replaceAll(":", "-")
                    .replaceAll("/", "-")
                    .replaceAll("\\?", "-")}-\{curr.name()
                    .replaceAll(":", "-")
                    .replaceAll("/", "-")
                    .replaceAll("\\?", "-")}";
            albumStore.put(name, curr);
            resultStore.put(name, res);
        }
    }
}
