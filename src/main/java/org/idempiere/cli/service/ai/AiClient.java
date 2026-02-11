package org.idempiere.cli.service.ai;

/**
 * Abstraction for AI code generation providers.
 * Implementations use java.net.http.HttpClient directly — no heavy SDKs.
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
     * Returns the provider name for logging and identification.
     */
    String providerName();
}
