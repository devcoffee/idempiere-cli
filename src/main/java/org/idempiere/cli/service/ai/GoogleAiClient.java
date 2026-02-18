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
 * Google Generative AI (Gemini) client.
 * POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent
 */
@ApplicationScoped
public class GoogleAiClient implements AiClient {

    private static final String API_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final String DEFAULT_MODEL = "gemini-2.5-flash";
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
        return "google";
    }

    @Override
    public AiResponse validate() {
        String apiKey = getApiKey();
        if (apiKey == null) {
            return AiResponse.fail("API key not configured");
        }
        try {
            ObjectNode root = objectMapper.createObjectNode();
            ArrayNode contents = root.putArray("contents");
            ObjectNode content = contents.addObject();
            ArrayNode parts = content.putArray("parts");
            ObjectNode part = parts.addObject();
            part.put("text", "hi");
            // Set maxOutputTokens to 1 for minimal cost
            ObjectNode genConfig = root.putObject("generationConfig");
            genConfig.put("maxOutputTokens", 1);
            String body = objectMapper.writeValueAsString(root);

            String model = getModel();
            String url = API_BASE + model + ":generateContent?key=" + apiKey;
            HttpResponse<String> response = sendRequest(url, body);
            if (response.statusCode() == 200) {
                return AiResponse.ok("OK");
            }
            try {
                JsonNode errorNode = objectMapper.readTree(response.body());
                String errorMsg = errorNode.path("error").path("message").asText(null);
                if (errorMsg != null) {
                    return AiResponse.fail(errorMsg);
                }
            } catch (Exception ignored) {}
            return AiResponse.fail("HTTP " + response.statusCode());
        } catch (IOException e) {
            return AiResponse.fail("Network error: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return AiResponse.fail("Request interrupted");
        } catch (Exception e) {
            return AiResponse.fail(e.getMessage());
        }
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

            return AiResponse.fail("Google AI API error " + response.statusCode() + ": " + response.body());
        } catch (IOException e) {
            return AiResponse.fail("Network error: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return AiResponse.fail("Request interrupted");
        }
    }

    HttpResponse<String> sendRequest(String url, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return AiHttpUtils.sendWithRetry(getHttpClient(), request);
    }

    private String buildRequestBody(String prompt) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            ArrayNode contents = root.putArray("contents");
            ObjectNode content = contents.addObject();
            ArrayNode parts = content.putArray("parts");
            ObjectNode part = parts.addObject();
            part.put("text", prompt);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build request body", e);
        }
    }

    /**
     * Extracts text from Google AI response.
     * Format: { "candidates": [{ "content": { "parts": [{ "text": "..." }] } }] }
     */
    String extractContent(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                JsonNode parts = candidates.get(0).path("content").path("parts");
                if (parts.isArray() && !parts.isEmpty()) {
                    return parts.get(0).path("text").asText(null);
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private String getApiKey() {
        CliConfig config = configService.loadConfig();
        return config.getAi().resolveApiKey("GOOGLE_API_KEY");
    }

    private String getModel() {
        CliConfig config = configService.loadConfig();
        String model = config.getAi().getModel();
        return (model != null && !model.isEmpty()) ? model : DEFAULT_MODEL;
    }
}
