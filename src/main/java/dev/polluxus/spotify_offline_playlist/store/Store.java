package dev.polluxus.spotify_offline_playlist.store;

public interface Store<T> {

    T get(String key);

    void put(String key, T value);
}
