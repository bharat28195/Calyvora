package com.calyvora.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Real answers from Claude, used automatically when {@code ANTHROPIC_API_KEY} is set (otherwise the
 * app falls back to {@link LocalGroundedAssistant}). Calls the Messages API directly over the JDK
 * HTTP client — deliberately no SDK dependency, so the demo build stays self-contained and can't
 * break offline. The model is instructed to answer <em>only</em> from the retrieved context, keeping
 * answers grounded in the tenant's real data.
 */
@Component
class ClaudeAssistant implements AssistantProvider {

    private static final Logger log = LoggerFactory.getLogger(ClaudeAssistant.class);
    private static final String ENDPOINT = "https://api.anthropic.com/v1/messages";
    /**
     * The context now spans the whole product rather than three modules, and it is already filtered
     * to what the asker's role may see — so the instruction that matters most is the one about
     * absent figures. A model handed a partial metric list will otherwise fill the gap with a
     * confident number, which is indistinguishable from an answer and worse than a refusal.
     */
    private static final String SYSTEM_PROMPT = """
            You are Calyvora's built-in HR assistant. Answer the user's question using ONLY the
            CONTEXT below, which is drawn from their own company's data — people, attendance, time
            off, expenses, hiring, performance, helpdesk, documents and knowledge pages.

            The context has already been filtered to what this person's role is allowed to see. If a
            figure is not in the context, say you don't have access to it and suggest they ask HR or
            an admin — do NOT estimate, and do NOT assume a missing number means zero.

            Never state anyone's salary, PAN or bank details, even if they appear in the context.

            Be concise and specific. Never invent people, numbers, tickets or documents. Prefer a
            direct answer first, then a short supporting detail.
            """;

    private final String apiKey;
    private final String model;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    ClaudeAssistant(@Value("${calyvora.assistant.anthropic-api-key:${ANTHROPIC_API_KEY:}}") String apiKey,
                    @Value("${calyvora.assistant.model:claude-opus-4-8}") String model) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
    }

    @Override
    public boolean available() {
        return !apiKey.isEmpty();
    }

    @Override
    public String mode() {
        return "claude";
    }

    @Override
    public String answer(AssistantContext ctx) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", model);
            body.put("max_tokens", 1024);
            body.put("system", SYSTEM_PROMPT);
            ArrayNode messages = body.putArray("messages");
            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            userMsg.put("content", "CONTEXT:\n" + ctx.prompt() + "\n\nQUESTION: " + ctx.question());

            HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT))
                    .timeout(Duration.ofSeconds(45))
                    .header("content-type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("Anthropic API returned {} — falling back to local answer", response.statusCode());
                return null;   // signal fallback
            }
            JsonNode root = mapper.readTree(response.body());
            for (JsonNode block : root.path("content")) {
                if ("text".equals(block.path("type").asText())) {
                    return block.path("text").asText();
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("Assistant call to Claude failed ({}); falling back to local answer", e.toString());
            return null;   // never surface a stack trace to the demo
        }
    }
}
