package com.calyvora.assistant;

import com.calyvora.assistant.dto.AssistantResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The cross-app assistant ({@code POST /api/v1/assistant/ask}). Tenant-scoped; auth required. */
@RestController
@RequestMapping("/api/v1/assistant")
public class AssistantController {

    private final AssistantService assistantService;

    public AssistantController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    public record AskRequest(@NotBlank @Size(max = 1000) String question) {}

    @PostMapping("/ask")
    public AssistantResponse ask(@RequestBody AskRequest request) {
        return assistantService.ask(request.question());
    }
}
