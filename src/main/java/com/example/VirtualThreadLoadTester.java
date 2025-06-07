package com.example;

import com.google.common.util.concurrent.RateLimiter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class VirtualThreadLoadTester {
    private static final String TARGET_URL = System.getenv().getOrDefault("TARGET_URL", "https://www.google.com");
    private static final int REQUESTS_PER_SECOND = Integer.parseInt(System.getenv().getOrDefault("REQUESTS_PER_SECOND", "1000"));
    private static final int TEST_DURATION_SECONDS = Integer.parseInt(System.getenv().getOrDefault("TEST_DURATION_SECONDS", "10"));

    public static void main(String[] args) {

        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        @SuppressWarnings("UnstableApiUsage")
        RateLimiter rateLimiter = RateLimiter.create(REQUESTS_PER_SECOND);

        AtomicInteger successfulRequests = new AtomicInteger(0);
        AtomicInteger failedRequests = new AtomicInteger(0);
        int totalRequests = REQUESTS_PER_SECOND * TEST_DURATION_SECONDS;

        System.out.printf("Starting test: %d RPS for %d seconds (total %d requests)...\n",
                REQUESTS_PER_SECOND, TEST_DURATION_SECONDS, totalRequests);
        long startTime = System.currentTimeMillis();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < totalRequests; i++) {
                rateLimiter.acquire();

                executor.submit(() -> {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(TARGET_URL))
                            .GET()
                            .build();
                    try {
                        // 使用同步的 send 方法。因為在虛擬執行緒中，阻塞不是問題。
                        // JVM 會在 I/O 等待時自動掛起虛擬執行緒，釋放核心執行緒。
                        HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                        if (response.statusCode() >= 200 && response.statusCode() < 300) {
                            successfulRequests.incrementAndGet();
                        } else {
                            failedRequests.incrementAndGet();
                        }
                    } catch (IOException | InterruptedException e) {
                        failedRequests.incrementAndGet();
                    }
                });
            }
        }

        long endTime = System.currentTimeMillis();
        double durationInSeconds = (endTime - startTime) / 1000.0;

        System.out.println("\n--- Test Finished ---");
        System.out.printf("Total duration: %.2f seconds\n", durationInSeconds);
        System.out.printf("Successful requests: %d\n", successfulRequests.get());
        System.out.printf("Failed requests: %d\n", failedRequests.get());
        System.out.printf("Actual RPS: %.2f\n", successfulRequests.get() / durationInSeconds);
    }
}
