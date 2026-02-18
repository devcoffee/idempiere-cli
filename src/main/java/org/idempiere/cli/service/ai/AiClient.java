package org.idempiere.cli.service.ai;

/**
 * Abstraction for AI code generation providers.
 *
 * <p>Uses {@link java.net.http.HttpClient} directly instead of provider SDKs.
 *
 * <p><b>Rationale:</b>
 * <ul>
 *   <li>Single endpoint usage (messages/generateContent/completions)</li>
 *   <li>Native image compatibility (no reflection-heavy SDKs)</li>
 *   <li>Multi-provider support without 3 SDK dependency trees</li>
 *   <li>Jackson handles JSON serialization; no manual string building</li>
 * </ul>
 *
 * <p>If future features require streaming (SSE), function calling, or
 * multi-turn conversations, reconsider adopting the official SDK
 * for that specific provider.
 */
public interface AiClient {

    /**
     * Checks if this provider is properly configured (API key available, etc.).
     */
    boolean isConfigured();

    /**
     * Generates a response from the given prompt.
     *
     * @param prompt the prompt to send
     * @return the AI response
     */
    AiResponse generate(String prompt);

    /**
     * Validates the AI connection by sending a minimal request.
     * Uses max_tokens=1 to minimize cost.
     *
     * @return the validation result
     */
    default AiResponse validate() {
        return generate("Reply with OK");
    }

    /**
     * Returns the provider name for logging and identification.
     */
    String providerName();
}
