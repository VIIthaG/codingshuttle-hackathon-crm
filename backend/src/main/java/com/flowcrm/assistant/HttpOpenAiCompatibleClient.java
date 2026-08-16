package com.flowcrm.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flowcrm.common.exception.ServiceUnavailableException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

public class HttpOpenAiCompatibleClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(HttpOpenAiCompatibleClient.class);

    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public HttpOpenAiCompatibleClient(AiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds()));
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public AiCompletion complete(AiRequest request) {
        try {
            ObjectNode body = buildChatCompletionsBody(objectMapper, properties.getModel(), request);

            String raw = restClient
                    .post()
                    .uri(trimSlash(properties.getBaseUrl()) + "/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .body(objectMapper.writeValueAsString(body))
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(raw == null ? "{}" : raw);
            log.info("Flow AI provider finishReason={}", finishReason(root));
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            if (content.isBlank()) {
                log.warn("Flow AI provider returned empty content");
                throw AiUnavailable.exception();
            }
            return new AiCompletion(content.trim());
        } catch (ServiceUnavailableException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            log.warn("Flow AI provider HTTP {}", ex.getStatusCode().value());
            throw AiUnavailable.exception();
        } catch (RestClientException ex) {
            log.warn("Flow AI provider network/timeout failure");
            throw AiUnavailable.exception();
        } catch (Exception ex) {
            log.warn("Flow AI provider response could not be parsed");
            throw AiUnavailable.exception();
        }
    }

    static ObjectNode buildChatCompletionsBody(ObjectMapper objectMapper, String model, AiRequest request) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", request.maxOutputTokens());
        body.put("temperature", 0.3);
        body.put("reasoning_effort", "low");
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", request.systemPrompt());
        for (AiChatMessage turn : request.history()) {
            if (turn == null || turn.role() == null || turn.content() == null) {
                continue;
            }
            String role = turn.role().trim().toLowerCase();
            if (!role.equals("user") && !role.equals("assistant")) {
                continue;
            }
            messages.addObject().put("role", role).put("content", turn.content());
        }
        messages.addObject().put("role", "user").put("content", request.userPrompt());
        return body;
    }

    static String finishReason(JsonNode root) {
        String reason = root.path("choices").path(0).path("finish_reason").asText("").trim();
        return reason.isEmpty() ? "unknown" : reason;
    }

    private static String trimSlash(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "https://api.openai.com/v1";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
