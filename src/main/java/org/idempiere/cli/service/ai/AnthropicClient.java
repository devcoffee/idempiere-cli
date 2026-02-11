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
 * Anthropic Claude API client.
 * POST https://api.anthropic.com/v1/messages
 */
@ApplicationScoped
public class AnthropicClient implements AiClient {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String API_VERSION = "2023-06-01";
    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final String DEFAULT_MODEL = "claude-sonnet-4-20250514";

    @Inject
    CliConfigService configService;

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

            return AiResponse.fail("Anthropic API error " + response.statusCode() + ": " + response.body());
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
                .header("x-api-key", apiKey)
                .header("anthropic-version", API_VERSION)
                .timeout(TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String buildRequestBody(String model, String prompt) {
        // Manual JSON construction to avoid Jackson dependency in this class
        return "{\"model\":\"" + escapeJson(model) + "\","
                + "\"max_tokens\":4096,"
                + "\"messages\":[{\"role\":\"user\",\"content\":\"" + escapeJson(prompt) + "\"}]}";
    }

    /**
     * Extracts the text content from an Anthropic API response.
     * Response format: { "content": [{ "type": "text", "text": "..." }] }
     */
    String extractContent(String responseBody) {
        // Simple JSON extraction without a JSON library
        int contentIdx = responseBody.indexOf("\"content\"");
        if (contentIdx < 0) return null;

        int textIdx = responseBody.indexOf("\"text\"", contentIdx);
        if (textIdx < 0) return null;

        int colonIdx = responseBody.indexOf(":", textIdx);
        if (colonIdx < 0) return null;

        // Find the opening quote of the text value
        int startQuote = responseBody.indexOf("\"", colonIdx + 1);
        if (startQuote < 0) return null;

        // Find the closing quote (handle escaped quotes)
        return extractJsonString(responseBody, startQuote);
    }

    private String getApiKey() {
        CliConfig config = configService.loadConfig();
        String envVar = config.getAi().getApiKeyEnv();
        if (envVar == null || envVar.isEmpty()) {
            envVar = "ANTHROPIC_API_KEY";
        }
        return System.getenv(envVar);
    }

    private String getModel() {
        CliConfig config = configService.loadConfig();
        String model = config.getAi().getModel();
        return (model != null && !model.isEmpty()) ? model : DEFAULT_MODEL;
    }

    static String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    static String extractJsonString(String json, int openQuoteIdx) {
        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        for (int i = openQuoteIdx + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                switch (c) {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    default -> { sb.append('\\'); sb.append(c); }
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return sb.toString();
            } else {
                sb.append(c);
            }
        }
        return null; // unterminated string
    }
}
