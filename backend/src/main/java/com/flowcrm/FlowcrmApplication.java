package com.flowcrm;

import com.flowcrm.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
public class FlowcrmApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlowcrmApplication.class, args);
    }
}
