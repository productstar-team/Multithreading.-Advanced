package com.edu.imageconversion.download;

import com.edu.imageconversion.download.dto.DownloadResult;
import jakarta.annotation.PreDestroy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Service
public class DownloadService {

    private static final Logger logger = LogManager.getLogger(DownloadService.class);
    private static final String DEFAULT_TARGET_DIR = "downloads";

    // Per-request HTTP timeout. The +5s at CompletableFuture level is a JVM-side
    // safety net in case HttpClient's own timeout doesn't fire (e.g. OS-level hang).
    private static final int HTTP_TIMEOUT_SECONDS = 30;

    // I/O-bound tasks spend most time waiting on the network, not on CPU.
    // corePoolSize = cores * 2: keeps enough threads active to saturate bandwidth
    //   without the overhead of creating them on demand.
    // maxPoolSize = 20: hard cap on concurrent connections; beyond this the queue absorbs bursts.
    // LinkedBlockingQueue(50): bounded — prevents OOM if callers submit faster than downloads complete.
    // CallerRunsPolicy: backpressure instead of silent rejection; the HTTP thread blocks,
    //   slowing ingest rather than losing work.
    private final ThreadPoolExecutor executor;

    private final HttpClient httpClient;

    public DownloadService() {
        int cores = Runtime.getRuntime().availableProcessors();
        this.executor = new ThreadPoolExecutor(
                cores * 2,
                20,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(50),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public List<DownloadResult> downloadAll(List<String> urls, String targetDir) {
        String resolvedDir = (targetDir != null && !targetDir.isBlank()) ? targetDir : DEFAULT_TARGET_DIR;

        Path targetPath;
        try {
            targetPath = Paths.get(resolvedDir).toAbsolutePath().normalize();
            Files.createDirectories(targetPath);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create target directory: " + resolvedDir, e);
        }

        // CompletableFuture.allOf chosen over ExecutorCompletionService:
        // the endpoint must return the full result list in one response, so processing
        // results as they arrive (ECS's strength) gives no advantage here.
        // allOf provides a single join point with minimal boilerplate.
        List<CompletableFuture<DownloadResult>> futures = urls.stream()
                .map(url -> {
                    long start = System.currentTimeMillis();
                    return CompletableFuture
                            .supplyAsync(() -> download(url, targetPath), executor)
                            .orTimeout(HTTP_TIMEOUT_SECONDS + 5, TimeUnit.SECONDS)
                            .exceptionally(ex -> DownloadResult.failed(
                                    url,
                                    rootMessage(ex),
                                    System.currentTimeMillis() - start
                            ));
                })
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        return futures.stream().map(CompletableFuture::join).toList();
    }

    private DownloadResult download(String url, Path targetPath) {
        long start = System.currentTimeMillis();
        String thread = Thread.currentThread().getName();
        logger.info("[{}] Starting download: {}", thread, url);

        try {
            URI uri = URI.create(url);
            String filename = sanitizeFilename(uri);
            Path filePath = uniquePath(targetPath, filename);

            // Prevent path traversal: normalized path must stay inside targetPath.
            if (!filePath.startsWith(targetPath)) {
                throw new SecurityException("Derived filename escapes target directory: " + filename);
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                    .GET()
                    .build();

            HttpResponse<Path> response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(filePath));
            long durationMs = System.currentTimeMillis() - start;

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                logger.info("[{}] Saved {} → {} in {}ms", thread, url, filePath, durationMs);
                return DownloadResult.success(url, filePath.toString(), durationMs);
            } else {
                Files.deleteIfExists(filePath);
                String msg = "HTTP " + response.statusCode();
                logger.warn("[{}] Failed {} in {}ms: {}", thread, url, durationMs, msg);
                return DownloadResult.failed(url, msg, durationMs);
            }

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - start;
            logger.error("[{}] Error downloading {} after {}ms: {}", thread, url, durationMs, e.getMessage());
            return DownloadResult.failed(url, e.getMessage(), durationMs);
        }
    }

    private String sanitizeFilename(URI uri) {
        String path = uri.getPath();
        if (path == null || path.isBlank()) {
            return "file_" + System.currentTimeMillis();
        }
        String name = path.substring(path.lastIndexOf('/') + 1);
        if (name.isBlank()) {
            name = "file_" + System.currentTimeMillis();
        }
        // Strip query params that might have been embedded in the path segment.
        int q = name.indexOf('?');
        if (q != -1) name = name.substring(0, q);
        // Allow only safe characters; replace everything else with underscore.
        name = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (name.isBlank() || name.equals(".") || name.equals("..")) {
            name = "file_" + System.currentTimeMillis();
        }
        return name;
    }

    // Appends _1, _2, … suffix when a file with this name already exists.
    private Path uniquePath(Path dir, String filename) {
        Path candidate = dir.resolve(filename);
        if (!Files.exists(candidate)) {
            return candidate;
        }
        String base = filename;
        String ext = "";
        int dot = filename.lastIndexOf('.');
        if (dot > 0) {
            base = filename.substring(0, dot);
            ext = filename.substring(dot);
        }
        int counter = 1;
        do {
            candidate = dir.resolve(base + "_" + counter++ + ext);
        } while (Files.exists(candidate));
        return candidate;
    }

    private String rootMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }

    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down DownloadService executor...");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("DownloadService executor shut down");
    }
}
