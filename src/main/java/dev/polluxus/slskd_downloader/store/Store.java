package dev.polluxus.slskd_downloader.store;

public interface Store<T> {

    boolean has(String key);

    T get(String key);

    void put(String key, T value);
}
