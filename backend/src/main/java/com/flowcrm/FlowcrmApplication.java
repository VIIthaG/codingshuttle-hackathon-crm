package com.flowcrm;

import com.flowcrm.assistant.AiProperties;
import com.flowcrm.config.CorsProperties;
import com.flowcrm.config.JwtProperties;
import com.flowcrm.lock.OutboxLockProperties;
import com.flowcrm.messaging.ReminderProperties;
import com.flowcrm.outbox.OutboxPublisherProperties;
import com.flowcrm.ratelimit.LoginRateLimitProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties({
    JwtProperties.class,
    CorsProperties.class,
    ReminderProperties.class,
    OutboxPublisherProperties.class,
    LoginRateLimitProperties.class,
    OutboxLockProperties.class,
    AiProperties.class
})
@EnableScheduling
public class FlowcrmApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlowcrmApplication.class, args);
    }
}
