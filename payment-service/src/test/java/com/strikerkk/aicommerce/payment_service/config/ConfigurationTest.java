package com.strikerkk.aicommerce.payment_service.config;

import com.razorpay.RazorpayClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("The configuration classes")
class ConfigurationTest {

    // ==================================================================
    // AppConfig
    // ==================================================================

    @Nested
    @DisplayName("AppConfig")
    class AppConfigTest {

        private final AppConfig appConfig = new AppConfig();

        @Test
        @DisplayName("is a configuration class")
        void isAConfigurationClass() {
            assertThat(AppConfig.class.getAnnotation(Configuration.class)).isNotNull();
        }

        @Test
        @DisplayName("declares the ModelMapper as a bean")
        void declaresTheModelMapperBean() throws Exception {
            assertThat(AppConfig.class.getMethod("modelMapper").getAnnotation(Bean.class)).isNotNull();
            assertThat(AppConfig.class.getMethod("modelMapper").getReturnType()).isEqualTo(ModelMapper.class);
        }

        @Test
        @DisplayName("builds a usable mapper")
        void buildsAUsableMapper() {
            assertThat(appConfig.modelMapper()).isNotNull();
        }

        @Test
        @DisplayName("leaves the mapper on its default configuration, the DTOs match by name")
        void leavesTheMapperOnItsDefaults() {
            ModelMapper mapper = appConfig.modelMapper();

            assertThat(mapper.getConfiguration().getMatchingStrategy())
                    .isEqualTo(org.modelmapper.convention.MatchingStrategies.STANDARD);
        }

        @Test
        @DisplayName("declares exactly one bean")
        void declaresExactlyOneBean() {
            assertThat(AppConfig.class.getDeclaredMethods()).hasSize(1);
        }
    }

    // ==================================================================
    // KafkaTopicConfig
    // ==================================================================

    @Nested
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
            assertThat(KafkaTopicConfig.class.getMethod("paymentSuccessTopic").getAnnotation(Bean.class))
                    .isNotNull();
            assertThat(KafkaTopicConfig.class.getMethod("paymentSuccessTopic").getReturnType())
                    .isEqualTo(NewTopic.class);
        }

        @Test
        @DisplayName("names the topic 'payment-success-topic' - order-service listens on it")
        void namesTheTopic() {
            assertThat(kafkaTopicConfig.paymentSuccessTopic().name()).isEqualTo("payment-success-topic");
        }

        @Test
        @DisplayName("spreads the topic over three partitions")
        void spreadsTheTopicOverThreePartitions() {
            assertThat(kafkaTopicConfig.paymentSuccessTopic().numPartitions()).isEqualTo(3);
        }

        @Test
        @DisplayName("keeps a single replica - one broker runs locally")
        void keepsASingleReplica() {
            assertThat(kafkaTopicConfig.paymentSuccessTopic().replicationFactor()).isEqualTo((short) 1);
        }

        @Test
        @DisplayName("declares exactly one topic")
        void declaresExactlyOneTopic() {
            assertThat(KafkaTopicConfig.class.getDeclaredMethods()).hasSize(1);
        }

        @Test
        @DisplayName("builds an equal topic every time it is called")
        void buildsAnEqualTopicEveryTime() {
            NewTopic first = kafkaTopicConfig.paymentSuccessTopic();
            NewTopic second = kafkaTopicConfig.paymentSuccessTopic();

            assertThat(first.name()).isEqualTo(second.name());
            assertThat(first.numPartitions()).isEqualTo(second.numPartitions());
        }
    }

    // ==================================================================
    // RazorpayConfig
    // ==================================================================

    @Nested
    @DisplayName("RazorpayConfig")
    class RazorpayConfigTest {

        private RazorpayConfig configuredWith(String keyId, String keySecret) {
            RazorpayConfig config = new RazorpayConfig();
            ReflectionTestUtils.setField(config, "keyId", keyId);
            ReflectionTestUtils.setField(config, "keySecret", keySecret);
            return config;
        }

        @Test
        @DisplayName("is a configuration class")
        void isAConfigurationClass() {
            assertThat(RazorpayConfig.class.getAnnotation(Configuration.class)).isNotNull();
        }

        @Test
        @DisplayName("declares the Razorpay client as a bean")
        void declaresTheClientBean() throws Exception {
            assertThat(RazorpayConfig.class.getMethod("razorpayClient").getAnnotation(Bean.class)).isNotNull();
            assertThat(RazorpayConfig.class.getMethod("razorpayClient").getReturnType())
                    .isEqualTo(RazorpayClient.class);
        }

        @Test
        @DisplayName("builds a client from the configured credentials")
        void buildsAClient() throws Exception {
            RazorpayClient client = configuredWith("rzp_test_dummykey", "dummysecret").razorpayClient();

            assertThat(client).isNotNull();
            assertThat(client.orders).isNotNull();
        }

        @Test
        @DisplayName("the client exposes the order sub client the service uses")
        void exposesTheOrderSubClient() throws Exception {
            RazorpayClient client = configuredWith("rzp_test_dummykey", "dummysecret").razorpayClient();

            assertThat(client.orders).isInstanceOf(com.razorpay.OrderClient.class);
        }

        @Test
        @DisplayName("declares exactly one bean")
        void declaresExactlyOneBean() {
            assertThat(RazorpayConfig.class.getDeclaredMethods()).hasSize(1);
        }

        @Test
        @DisplayName("keeps both credentials private, they never leave the configuration")
        void keepsTheCredentialsPrivate() {
            assertThat(RazorpayConfig.class.getDeclaredFields())
                    .filteredOn(f -> !f.isSynthetic())
                    .hasSize(2)
                    .allMatch(f -> java.lang.reflect.Modifier.isPrivate(f.getModifiers()));
        }
    }
}

