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
 * Anthropic Claude API client.
 * POST https://api.anthropic.com/v1/messages
 */
@ApplicationScoped
public class AnthropicClient implements AiClient {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String API_VERSION = "2023-06-01";
    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final String DEFAULT_MODEL = "claude-sonnet-4-20250514";
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
        return "anthropic";
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
                return AiResponse.fail("Failed to parse Anthropic response");
            }

            return AiResponse.fail("Anthropic API error " + response.statusCode() + ": " + response.body());
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
                .header("x-api-key", apiKey)
                .header("anthropic-version", API_VERSION)
                .timeout(TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return AiHttpUtils.sendWithRetry(getHttpClient(), request);
    }

    private String buildRequestBody(String model, String prompt) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("model", model);
            root.put("max_tokens", 4096);

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
     * Extracts the text content from an Anthropic API response.
     * Response format: { "content": [{ "type": "text", "text": "..." }] }
     */
    String extractContent(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode content = root.path("content");
            if (content.isArray() && !content.isEmpty()) {
                JsonNode firstBlock = content.get(0);
                if ("text".equals(firstBlock.path("type").asText())) {
                    return firstBlock.path("text").asText(null);
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private String getApiKey() {
        CliConfig config = configService.loadConfig();
        return config.getAi().resolveApiKey("ANTHROPIC_API_KEY");
    }

    private String getModel() {
        CliConfig config = configService.loadConfig();
        String model = config.getAi().getModel();
        return (model != null && !model.isEmpty()) ? model : DEFAULT_MODEL;
    }
}
