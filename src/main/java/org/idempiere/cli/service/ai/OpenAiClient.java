package org.idempiere.cli.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private volatile HttpClient httpClient;

    @Inject
    CliConfigService configService;

    private HttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = HttpClient.newBuilder()
                    .connectTimeout(TIMEOUT)
                    .build();
        }
        return httpClient;
    }

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

            return AiResponse.fail("OpenAI API error " + response.statusCode() + ": " + response.body());
        } catch (IOException e) {
            return AiResponse.fail("Network error: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return AiResponse.fail("Request interrupted");
        }
    }

    HttpResponse<String> sendRequest(String apiKey, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return AiHttpUtils.sendWithRetry(getHttpClient(), request);
    }

    private String buildRequestBody(String model, String prompt) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("model", model);
            ArrayNode messages = root.putArray("messages");
            ObjectNode msg = messages.addObject();
            msg.put("role", "user");
            msg.put("content", prompt);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build request body", e);
        }
    }

    /**
     * Extracts text from OpenAI response.
     * Format: { "choices": [{ "message": { "content": "..." } }] }
     */
    String extractContent(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                return choices.get(0).path("message").path("content").asText(null);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
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
