package com.flowcrm.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiClientConfig {

    @Bean
    AiClient aiClient(AiProperties properties, ObjectMapper objectMapper) {
        if (!properties.isReady()) {
            return new DisabledAiClient();
        }
        return new HttpOpenAiCompatibleClient(properties, objectMapper);
    }
}
