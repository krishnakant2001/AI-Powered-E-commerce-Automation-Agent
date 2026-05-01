package com.strikerkk.aicommerce.order_service.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic orderConfirmedTopic () {
        return new NewTopic("order-confirmed-topic", 3, (short) 1);
    }
}
