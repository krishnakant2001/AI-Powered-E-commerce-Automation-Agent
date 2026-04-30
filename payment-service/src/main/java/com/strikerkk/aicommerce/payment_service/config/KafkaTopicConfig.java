package com.strikerkk.aicommerce.payment_service.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic paymentSuccessTopic () {
        return new NewTopic("payment-success-topic", 3, (short) 1);
    }
}
