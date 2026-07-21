package com.calyvora.assistant.dto;

import java.util.List;

/**
 * An answer from the cross-app assistant, grounded in the tenant's own People/Work/Knowledge data.
 * {@code mode} is {@code "claude"} when a real model answered, {@code "local"} for the offline
 * grounded fallback — the UI can badge it. {@code sources} are the records the answer drew on.
 */
public record AssistantResponse(String answer, String mode, List<Source> sources) {

    public record Source(String kind, String title, String href) {}
}
