package com.flowcrm.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AiPropertiesTest {

    @Test
    void defaultMaxOutputTokens_isOneThousand() {
        assertThat(new AiProperties().getMaxOutputTokens()).isEqualTo(1000);
    }
}
