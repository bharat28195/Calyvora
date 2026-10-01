package com.calyvora.assistant;

/**
 * Pluggable answer engine for the assistant. Two implementations ship: an always-available offline
 * grounded provider, and a Claude-backed one that activates only when an API key is configured.
 * The service picks the highest-quality available provider at request time.
 */
interface AssistantProvider {

    /** Whether this provider can serve a request right now (e.g. an API key is configured). */
    boolean available();

    /** Badge shown to the user: {@code "claude"} or {@code "local"}. */
    String mode();

    /** Produce an answer grounded strictly in {@code context}. */
    String answer(AssistantContext context);
}
