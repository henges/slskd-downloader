package dev.polluxus.slskd_downloader.util;

import java.util.concurrent.*;

public class FutureUtils {

    public static <T> T get(Future<T> future, long timeout, TimeUnit unit) {

        try {
            return future.get(timeout, unit);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    public static void sleep(long timeout) {

        try {
            Thread.sleep(timeout);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
