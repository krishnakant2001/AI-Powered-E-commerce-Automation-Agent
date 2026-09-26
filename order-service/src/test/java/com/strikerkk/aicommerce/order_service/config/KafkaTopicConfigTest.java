package com.strikerkk.aicommerce.order_service.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("KafkaTopicConfig")
class KafkaTopicConfigTest {

    private final KafkaTopicConfig kafkaTopicConfig = new KafkaTopicConfig();

    @Test
    @DisplayName("is a configuration class")
    void isAConfigurationClass() {
        assertThat(KafkaTopicConfig.class.getAnnotation(Configuration.class)).isNotNull();
    }

    @Test
    @DisplayName("declares the topic as a bean, so Kafka creates it on start up")
    void declaresTheTopicBean() throws Exception {
        assertThat(KafkaTopicConfig.class.getMethod("orderConfirmedTopic").getAnnotation(Bean.class))
                .isNotNull();
        assertThat(KafkaTopicConfig.class.getMethod("orderConfirmedTopic").getReturnType())
                .isEqualTo(NewTopic.class);
    }

    @Test
    @DisplayName("names the topic 'order-confirmed-topic' - payment- and product-service listen on it")
    void namesTheTopic() {
        assertThat(kafkaTopicConfig.orderConfirmedTopic().name()).isEqualTo("order-confirmed-topic");
    }

    @Test
    @DisplayName("spreads the topic over three partitions")
    void spreadsTheTopicOverThreePartitions() {
        assertThat(kafkaTopicConfig.orderConfirmedTopic().numPartitions()).isEqualTo(3);
    }

    @Test
    @DisplayName("keeps a single replica - one broker runs locally")
    void keepsASingleReplica() {
        assertThat(kafkaTopicConfig.orderConfirmedTopic().replicationFactor()).isEqualTo((short) 1);
    }

    @Test
    @DisplayName("declares exactly one topic")
    void declaresExactlyOneTopic() {
        assertThat(KafkaTopicConfig.class.getDeclaredMethods()).hasSize(1);
    }

    @Test
    @DisplayName("builds an equal topic every time it is called")
    void buildsAnEqualTopicEveryTime() {
        NewTopic first = kafkaTopicConfig.orderConfirmedTopic();
        NewTopic second = kafkaTopicConfig.orderConfirmedTopic();

        assertThat(first.name()).isEqualTo(second.name());
        assertThat(first.numPartitions()).isEqualTo(second.numPartitions());
    }
}

