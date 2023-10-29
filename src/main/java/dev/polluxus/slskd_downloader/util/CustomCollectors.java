package dev.polluxus.slskd_downloader.util;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class CustomCollectors {

    public static <T, K> Collector<T, ?, Map<K, T>>
    keyingBy(Function<? super T, ? extends K> classifier) {

        return Collectors.toMap(classifier, Function.identity());
    }
}
