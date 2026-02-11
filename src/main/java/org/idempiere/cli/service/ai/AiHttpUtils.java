package org.idempiere.cli.service.ai;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Shared HTTP utilities for AI clients.
 * Provides retry with exponential backoff for transient errors.
 */
public final class AiHttpUtils {

    private static final int MAX_RETRIES = 2;  // total 3 attempts
    private static final long BASE_DELAY_MS = 1000;

    private AiHttpUtils() {}

    /**
     * Sends HTTP request with retry on 429 (rate limit) and 5xx (server error).
     * Uses exponential backoff: 1s, 2s between retries.
     * Respects Retry-After header when present.
     */
    public static HttpResponse<String> sendWithRetry(HttpClient client, HttpRequest request)
            throws IOException, InterruptedException {

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        for (int attempt = 0; attempt < MAX_RETRIES && isRetryable(response.statusCode()); attempt++) {
            long delay = BASE_DELAY_MS * (1L << attempt);  // 1s, 2s

            String retryAfter = response.headers().firstValue("retry-after").orElse(null);
            if (retryAfter != null) {
                try {
                    delay = Math.max(delay, Long.parseLong(retryAfter) * 1000);
                } catch (NumberFormatException ignored) {}
            }

            Thread.sleep(delay);
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        }

        return response;
    }

    static boolean isRetryable(int statusCode) {
        return statusCode == 429 || statusCode >= 500;
    }
}
