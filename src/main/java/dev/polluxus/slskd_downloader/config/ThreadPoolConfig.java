package dev.polluxus.slskd_downloader.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// TODO: Centralise thread pool instantiation here
public class ThreadPoolConfig {

    public static ExecutorService VIRTUAL_THREAD_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
}
