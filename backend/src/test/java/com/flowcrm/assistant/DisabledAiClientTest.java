package com.flowcrm.assistant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DisabledAiClientTest {

    @Test
    void complete_throwsUnavailableWithoutCallingNetwork() {
        DisabledAiClient client = new DisabledAiClient();
        assertThatThrownBy(() -> client.complete(new AiRequest("sys", java.util.List.of(), "user", 10)))
                .isInstanceOf(com.flowcrm.common.exception.ServiceUnavailableException.class)
                .hasMessage(AiUnavailable.USER_MESSAGE);
    }
}
