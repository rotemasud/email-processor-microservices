package com.emailprocessor.processor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EmailProcessorConsumerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmailProcessorConsumerApplication.class, args);
    }

}
