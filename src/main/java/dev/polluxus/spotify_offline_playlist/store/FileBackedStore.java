package dev.polluxus.spotify_offline_playlist.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.polluxus.spotify_offline_playlist.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.michaelthelin.spotify.model_objects.specification.Playlist;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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

        final File f = Path.of(config.dataDirectory()).toFile();
        f.mkdirs();
        final ObjectMapper mapper = new ObjectMapper();
        return new FileBackedStore<>(f, mapper, klazz);
    }

    @Override
    public T get(final String key) {

        final File data = Path.of(parentDir.getPath(), key + ".json").toFile();
        if (!data.exists()) {
            return null;
        }
        try {
            InputStream in = new FileInputStream(data);
            final String s = new String(in.readAllBytes());
            log.info("{}", s);
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
             final boolean existed = data.createNewFile();
             if (existed) {
                 log.info("Overwriting file {} ({} bytes)...", data.getPath(), data.length());
             }
             mapper.writeValue(data, value);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
