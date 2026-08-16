package com.flowcrm.assistant;

public class DisabledAiClient implements AiClient {

    @Override
    public AiCompletion complete(AiRequest request) {
        throw AiUnavailable.exception();
    }
}
