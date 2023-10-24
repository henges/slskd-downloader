package dev.polluxus.slskd_downloader.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.polluxus.slskd_downloader.config.Config;
import dev.polluxus.slskd_downloader.config.JacksonConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class FileBackedStore<T> implements Store<T> {

    private static final Logger log = LoggerFactory.getLogger(FileBackedStore.class);

    private final File parentDir;
    private final ObjectMapper mapper;
    private final Class<T> klazz;

    FileBackedStore(final File f, final ObjectMapper mapper, final Class<T> klazz) {
        this.parentDir = f;
        this.mapper = mapper;
        this.klazz = klazz;
    }

    public static <T> FileBackedStore<T> from(final Config config, final Class<T> klazz) {

        final File f = Path.of(config.dataDirectory(), klazz.getSimpleName()).toFile();
        f.mkdirs();
        final ObjectMapper mapper = JacksonConfig.MAPPER;
        return new FileBackedStore<>(f, mapper, klazz);
    }

    @Override
    public boolean has(String key) {

        final File data = Path.of(parentDir.getPath(), key + ".json").toFile();
        return data.exists();
    }

    @Override
    public T get(final String key) {

        final File data = Path.of(parentDir.getPath(), key + ".json").toFile();
        if (!data.exists()) {
            return null;
        }
        try {
            return mapper.readValue(data, klazz);
        } catch (IOException e) {
            log.error("Error reading path {}", data.getPath(), e);
            return null;
        }
    }

    @Override
    public void put(final String key, final T value) {

        final File data = Path.of(parentDir.getPath(), key + ".json").toFile();
        try {
             if (data.exists()) {
                 log.info("Overwriting file {} ({} bytes)...", data.getPath(), data.length());
             }
             data.createNewFile();
             mapper.writeValue(data, value);
        } catch (IOException e) {
            log.error("Error saving file {}", data.getPath(), e);
            // Actually just swallow the exception - the cache is best effort.
            // throw new RuntimeException(e);
        }
    }
}
