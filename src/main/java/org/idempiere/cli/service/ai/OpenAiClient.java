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
 * OpenAI Chat Completions API client.
 * POST https://api.openai.com/v1/chat/completions
 */
@ApplicationScoped
public class OpenAiClient implements AiClient {

    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final String DEFAULT_MODEL = "gpt-4o";

    @Inject
    CliConfigService configService;

    @Override
    public boolean isConfigured() {
        return getApiKey() != null;
    }

    @Override
    public String providerName() {
        return "openai";
    }

    @Override
    public AiResponse generate(String prompt) {
        String apiKey = getApiKey();
        if (apiKey == null) {
            return AiResponse.fail("API key not configured. Set the environment variable specified in ai.apiKeyEnv.");
        }

        String model = getModel();
        String requestBody = buildRequestBody(model, prompt);

        try {
            HttpResponse<String> response = sendRequest(apiKey, requestBody);

            if (response.statusCode() == 200) {
                String content = extractContent(response.body());
                if (content != null) {
                    return AiResponse.ok(content);
                }
                return AiResponse.fail("Failed to parse OpenAI response");
            }

            // Retry once on 5xx
            if (response.statusCode() >= 500) {
                response = sendRequest(apiKey, requestBody);
                if (response.statusCode() == 200) {
                    String content = extractContent(response.body());
                    if (content != null) {
                        return AiResponse.ok(content);
                    }
                }
            }

            return AiResponse.fail("OpenAI API error " + response.statusCode() + ": " + response.body());
        } catch (IOException e) {
            return AiResponse.fail("Network error: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return AiResponse.fail("Request interrupted");
        }
    }

    private HttpResponse<String> sendRequest(String apiKey, String body) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String buildRequestBody(String model, String prompt) {
        return "{\"model\":\"" + AnthropicClient.escapeJson(model) + "\","
                + "\"messages\":[{\"role\":\"user\",\"content\":\"" + AnthropicClient.escapeJson(prompt) + "\"}]}";
    }

    /**
     * Extracts text from OpenAI response.
     * Format: { "choices": [{ "message": { "content": "..." } }] }
     */
    String extractContent(String responseBody) {
        int choicesIdx = responseBody.indexOf("\"choices\"");
        if (choicesIdx < 0) return null;

        // Find "content" within choices (skip role content)
        int messageIdx = responseBody.indexOf("\"message\"", choicesIdx);
        if (messageIdx < 0) return null;

        int contentIdx = responseBody.indexOf("\"content\"", messageIdx);
        if (contentIdx < 0) return null;

        int colonIdx = responseBody.indexOf(":", contentIdx);
        if (colonIdx < 0) return null;

        int startQuote = responseBody.indexOf("\"", colonIdx + 1);
        if (startQuote < 0) return null;

        return AnthropicClient.extractJsonString(responseBody, startQuote);
    }

    private String getApiKey() {
        CliConfig config = configService.loadConfig();
        String envVar = config.getAi().getApiKeyEnv();
        if (envVar == null || envVar.isEmpty()) {
            envVar = "OPENAI_API_KEY";
        }
        return System.getenv(envVar);
    }

    private String getModel() {
        CliConfig config = configService.loadConfig();
        String model = config.getAi().getModel();
        return (model != null && !model.isEmpty()) ? model : DEFAULT_MODEL;
    }
}
