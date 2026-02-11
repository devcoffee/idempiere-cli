package org.idempiere.cli.service.ai;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class AiClientTest {

    @Inject
    AiClientFactory aiClientFactory;

    @Inject
    AnthropicClient anthropicClient;

    @Inject
    GoogleAiClient googleAiClient;

    @Inject
    OpenAiClient openAiClient;

    @Test
    void testFactoryReturnsEmptyWhenNotConfigured() {
        // Default config has provider=none
        Optional<AiClient> client = aiClientFactory.getClient();
        assertTrue(client.isEmpty());
    }

    @Test
    void testAnthropicProviderName() {
        assertEquals("anthropic", anthropicClient.providerName());
    }

    @Test
    void testGoogleProviderName() {
        assertEquals("google", googleAiClient.providerName());
    }

    @Test
    void testOpenAiProviderName() {
        assertEquals("openai", openAiClient.providerName());
    }

    @Test
    void testAnthropicNotConfiguredWithoutApiKey() {
        // Without env var set, should not be configured
        // (The env var ANTHROPIC_API_KEY is not set in test environment)
        assertFalse(anthropicClient.isConfigured());
    }

    @Test
    void testAnthropicGenerateFailsWithoutApiKey() {
        AiResponse response = anthropicClient.generate("test prompt");
        assertFalse(response.success());
        assertNotNull(response.error());
        assertNull(response.content());
    }

    @Test
    void testGoogleGenerateFailsWithoutApiKey() {
        AiResponse response = googleAiClient.generate("test prompt");
        assertFalse(response.success());
        assertNotNull(response.error());
    }

    @Test
    void testOpenAiGenerateFailsWithoutApiKey() {
        AiResponse response = openAiClient.generate("test prompt");
        assertFalse(response.success());
        assertNotNull(response.error());
    }

    // ========== JSON parsing tests ==========

    @Test
    void testAnthropicExtractContent() {
        String responseBody = """
                {"id":"msg_123","type":"message","content":[{"type":"text","text":"Hello world"}],"model":"claude-sonnet-4-20250514"}""";
        String content = anthropicClient.extractContent(responseBody);
        assertEquals("Hello world", content);
    }

    @Test
    void testAnthropicExtractContentWithEscapes() {
        String responseBody = """
                {"content":[{"type":"text","text":"line1\\nline2\\t\\"quoted\\""}]}""";
        String content = anthropicClient.extractContent(responseBody);
        assertEquals("line1\nline2\t\"quoted\"", content);
    }

    @Test
    void testAnthropicExtractContentFromEmptyResponse() {
        assertNull(anthropicClient.extractContent("{}"));
        assertNull(anthropicClient.extractContent(""));
    }

    @Test
    void testGoogleExtractContent() {
        String responseBody = """
                {"candidates":[{"content":{"parts":[{"text":"Hello from Gemini"}]}}]}""";
        String content = googleAiClient.extractContent(responseBody);
        assertEquals("Hello from Gemini", content);
    }

    @Test
    void testOpenAiExtractContent() {
        String responseBody = """
                {"choices":[{"index":0,"message":{"role":"assistant","content":"Hello from GPT"},"finish_reason":"stop"}]}""";
        String content = openAiClient.extractContent(responseBody);
        assertEquals("Hello from GPT", content);
    }

    @Test
    void testAiResponseFactoryMethods() {
        AiResponse ok = AiResponse.ok("result");
        assertTrue(ok.success());
        assertEquals("result", ok.content());
        assertNull(ok.error());

        AiResponse fail = AiResponse.fail("error msg");
        assertFalse(fail.success());
        assertNull(fail.content());
        assertEquals("error msg", fail.error());
    }

    // ========== Multiple blocks / edge cases ==========

    @Test
    void testAnthropicExtractContentMultipleBlocks() {
        String response = """
                {"content":[{"type":"text","text":"first"},{"type":"text","text":"second"}]}""";
        assertEquals("first", anthropicClient.extractContent(response));
    }

    @Test
    void testAnthropicExtractContentMalformedJson() {
        assertNull(anthropicClient.extractContent("{malformed"));
        assertNull(anthropicClient.extractContent("not json at all"));
    }

    @Test
    void testGoogleExtractContentEmpty() {
        assertNull(googleAiClient.extractContent("{}"));
        assertNull(googleAiClient.extractContent("{\"candidates\":[]}"));
    }

    @Test
    void testOpenAiExtractContentEmpty() {
        assertNull(openAiClient.extractContent("{}"));
        assertNull(openAiClient.extractContent("{\"choices\":[]}"));
    }

    // ========== AiHttpUtils ==========

    @Test
    void testIsRetryable() {
        assertTrue(AiHttpUtils.isRetryable(429));
        assertTrue(AiHttpUtils.isRetryable(500));
        assertTrue(AiHttpUtils.isRetryable(503));
        assertFalse(AiHttpUtils.isRetryable(200));
        assertFalse(AiHttpUtils.isRetryable(400));
        assertFalse(AiHttpUtils.isRetryable(401));
        assertFalse(AiHttpUtils.isRetryable(404));
    }
}
