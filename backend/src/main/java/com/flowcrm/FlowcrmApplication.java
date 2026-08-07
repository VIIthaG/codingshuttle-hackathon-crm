package com.flowcrm;

import com.flowcrm.config.JwtProperties;
import com.flowcrm.messaging.ReminderProperties;
import com.flowcrm.outbox.OutboxPublisherProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties({JwtProperties.class, ReminderProperties.class, OutboxPublisherProperties.class})
@EnableScheduling
public class FlowcrmApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlowcrmApplication.class, args);
    }
}
