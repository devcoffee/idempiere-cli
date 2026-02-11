package org.idempiere.cli.service.ai;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.idempiere.cli.model.CliConfig;
import org.idempiere.cli.service.CliConfigService;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Google Generative AI (Gemini) client.
 * POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent
 */
@ApplicationScoped
public class GoogleAiClient implements AiClient {

    private static final String API_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final String DEFAULT_MODEL = "gemini-2.5-flash";

    @Inject
    CliConfigService configService;

    @Override
    public boolean isConfigured() {
        return getApiKey() != null;
    }

    @Override
    public String providerName() {
        return "google";
    }

    @Override
    public AiResponse generate(String prompt) {
        String apiKey = getApiKey();
        if (apiKey == null) {
            return AiResponse.fail("API key not configured. Set the environment variable specified in ai.apiKeyEnv.");
        }

        String model = getModel();
        String requestBody = buildRequestBody(prompt);
        String url = API_BASE + model + ":generateContent?key=" + apiKey;

        try {
            HttpResponse<String> response = sendRequest(url, requestBody);

            if (response.statusCode() == 200) {
                String content = extractContent(response.body());
                if (content != null) {
                    return AiResponse.ok(content);
                }
                return AiResponse.fail("Failed to parse Google AI response");
            }

            // Retry once on 5xx
            if (response.statusCode() >= 500) {
                response = sendRequest(url, requestBody);
                if (response.statusCode() == 200) {
                    String content = extractContent(response.body());
                    if (content != null) {
                        return AiResponse.ok(content);
                    }
                }
            }

            return AiResponse.fail("Google AI API error " + response.statusCode() + ": " + response.body());
        } catch (IOException e) {
            return AiResponse.fail("Network error: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return AiResponse.fail("Request interrupted");
        }
    }

    private HttpResponse<String> sendRequest(String url, String body) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String buildRequestBody(String prompt) {
        return "{\"contents\":[{\"parts\":[{\"text\":\"" + AnthropicClient.escapeJson(prompt) + "\"}]}]}";
    }

    /**
     * Extracts text from Google AI response.
     * Format: { "candidates": [{ "content": { "parts": [{ "text": "..." }] } }] }
     */
    String extractContent(String responseBody) {
        int candidatesIdx = responseBody.indexOf("\"candidates\"");
        if (candidatesIdx < 0) return null;

        int textIdx = responseBody.indexOf("\"text\"", candidatesIdx);
        if (textIdx < 0) return null;

        int colonIdx = responseBody.indexOf(":", textIdx);
        if (colonIdx < 0) return null;

        int startQuote = responseBody.indexOf("\"", colonIdx + 1);
        if (startQuote < 0) return null;

        return AnthropicClient.extractJsonString(responseBody, startQuote);
    }

    private String getApiKey() {
        CliConfig config = configService.loadConfig();
        String envVar = config.getAi().getApiKeyEnv();
        if (envVar == null || envVar.isEmpty()) {
            envVar = "GOOGLE_API_KEY";
        }
        return System.getenv(envVar);
    }

    private String getModel() {
        CliConfig config = configService.loadConfig();
        String model = config.getAi().getModel();
        return (model != null && !model.isEmpty()) ? model : DEFAULT_MODEL;
    }
}
