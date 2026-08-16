package com.flowcrm.assistant;

import com.flowcrm.assistant.dto.AssistantChatRequest;
import com.flowcrm.assistant.dto.AssistantChatResponse;
import com.flowcrm.common.exception.ErrorResponse;
import com.flowcrm.config.OpenApiConfig;
import com.flowcrm.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assistant")
@Tag(name = "Flow AI")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class AssistantController {

    private final AssistantService assistantService;

    public AssistantController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @PostMapping("/chat")
    @Operation(
            summary = "Flow AI chat",
            description =
                    """
                    Read-only, permission-aware assistant. The backend builds CRM context using the same ADMIN / \
                    SALES_REP rules as the rest of the API. The LLM never queries the database and never mutates CRM data.

                    Optional `context.entityType` + `context.entityId` (LEAD, ACCOUNT, CONTACT, DEAL) attaches that \
                    record if the caller can access it. Optional `history` is at most 8 prior user/assistant turns.

                    The provider is optional. If Flow AI is disabled, unconfigured, or the provider fails, the API \
                    returns HTTP 503 without leaking credentials or prompts.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Assistant answer"),
        @ApiResponse(
                responseCode = "400",
                description = "Validation failed",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Unauthorized",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "403",
                description = "Inaccessible record context",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "503",
                description = "Flow AI provider unavailable",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public AssistantChatResponse chat(
            @Valid @RequestBody AssistantChatRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return assistantService.chat(request, principal);
    }
}
