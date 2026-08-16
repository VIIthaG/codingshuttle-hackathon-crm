package com.flowcrm.assistant;

import com.flowcrm.assistant.dto.AssistantChatRequest;
import com.flowcrm.assistant.dto.AssistantChatResponse;
import com.flowcrm.assistant.dto.AssistantContextUsedResponse;
import com.flowcrm.assistant.dto.BuiltAssistantContext;
import com.flowcrm.common.exception.BadRequestException;
import com.flowcrm.enums.RelatedRecordType;
import com.flowcrm.enums.Role;
import com.flowcrm.security.UserPrincipal;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);

    private final AssistantContextBuilder contextBuilder;
    private final AiClient aiClient;
    private final AiProperties aiProperties;

    public AssistantService(
            AssistantContextBuilder contextBuilder, AiClient aiClient, AiProperties aiProperties) {
        this.contextBuilder = contextBuilder;
        this.aiClient = aiClient;
        this.aiProperties = aiProperties;
    }

    @Transactional(readOnly = true)
    public AssistantChatResponse chat(AssistantChatRequest request, UserPrincipal principal) {
        String message = request.message() == null ? "" : request.message().trim();
        if (message.isEmpty()) {
            throw new BadRequestException("Message is required");
        }

        long started = System.currentTimeMillis();
        BuiltAssistantContext built = contextBuilder.build(principal, request.context());
        AiRequest aiRequest =
                AssistantChatAssembler.assemble(message, request.history(), built.crmData(), aiProperties.getMaxOutputTokens());

        RelatedRecordType entityType = built.entityType();
        log.info(
                "Flow AI chat userId={} role={} entityType={} entityId={} crmChars={}",
                principal.getId(),
                principal.getRole(),
                entityType,
                built.entityId(),
                built.crmData().length());

        try {
            AiCompletion completion = aiClient.complete(aiRequest);
            log.info(
                    "Flow AI chat success userId={} latencyMs={}",
                    principal.getId(),
                    System.currentTimeMillis() - started);
            return new AssistantChatResponse(
                    completion.text(),
                    toUsed(built),
                    suggestions(principal.getRole(), entityType));
        } catch (RuntimeException ex) {
            log.info(
                    "Flow AI chat failure userId={} latencyMs={}",
                    principal.getId(),
                    System.currentTimeMillis() - started);
            throw ex;
        }
    }

    private static AssistantContextUsedResponse toUsed(BuiltAssistantContext built) {
        if (built.entityType() == null || built.entityId() == null) {
            return null;
        }
        return new AssistantContextUsedResponse(built.entityType(), built.entityId(), built.label());
    }

    static List<String> suggestions(Role role, RelatedRecordType entityType) {
        List<String> items = new ArrayList<>();
        if (entityType != null) {
            items.add("Summarize this record");
            items.add("Suggest next action");
            items.add("Draft a follow-up");
            items.add("What should I pay attention to?");
            return items;
        }
        items.add("What should I focus on today?");
        items.add("Summarize my pipeline");
        items.add("Show my overdue follow-ups");
        items.add("Which deals need attention?");
        if (role == Role.ADMIN) {
            items.add("Summarize team workload");
            items.add("Which reps have the most open pipeline?");
        }
        return items;
    }
}
