package com.calyvora.assistant;

import com.calyvora.assistant.dto.AssistantResponse.Source;

import java.util.List;
import java.util.Map;

/**
 * Everything the assistant retrieved for one question — the RAG payload. {@code prompt} is the
 * human-readable grounding text handed to a language model; {@code metrics}, {@code people}, and
 * {@code snippets} let the offline provider answer common questions structurally without a model.
 */
record AssistantContext(
        String question,
        String prompt,
        Map<String, Long> metrics,
        List<String> people,
        List<String> snippets,
        List<Source> sources
) {}
