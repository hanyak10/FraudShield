package com.fraudshield;

import com.fraudshield.config.MockProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(MockProperties.class)
public class FraudShieldApplication {

    public static void main(String[] args) {
        SpringApplication.run(FraudShieldApplication.class, args);
    }
}
