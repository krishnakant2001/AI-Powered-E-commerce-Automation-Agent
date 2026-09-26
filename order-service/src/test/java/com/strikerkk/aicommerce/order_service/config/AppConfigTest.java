package com.strikerkk.aicommerce.order_service.config;

import com.strikerkk.aicommerce.order_service.entity.Order;
import com.strikerkk.aicommerce.order_service.dto.response.OrderResponse;
import com.strikerkk.aicommerce.order_service.support.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

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
    }

    @Test
    @DisplayName("hands out a usable ModelMapper")
    void handsOutAUsableModelMapper() {
        ModelMapper modelMapper = appConfig.modelMapper();

        assertThat(modelMapper).isNotNull();
        assertThat(modelMapper.getConfiguration()).isNotNull();
    }

    @Test
    @DisplayName("keeps the default - standard - matching strategy")
    void keepsTheDefaultStrategy() {
        assertThat(appConfig.modelMapper().getConfiguration().getMatchingStrategy())
                .isEqualTo(org.modelmapper.convention.MatchingStrategies.STANDARD);
    }

    @Test
    @DisplayName("the mapper it hands out really maps an order onto its response")
    void theMapperMapsAnOrder() {
        Order order = TestDataFactory.order();

        OrderResponse response = appConfig.modelMapper().map(order, OrderResponse.class);

        assertThat(response.getId()).isEqualTo(TestDataFactory.ORDER_ID);
        assertThat(response.getUserId()).isEqualTo(TestDataFactory.USER_ID);
        assertThat(response.getStatus()).isEqualTo(order.getStatus());
        assertThat(response.getOrderItems()).hasSize(1);
        assertThat(response.getOrderItems().getFirst().getProductName())
                .isEqualTo(TestDataFactory.PRODUCT_NAME);
    }

    @Test
    @DisplayName("hands out a fresh instance per call - Spring turns it into a singleton")
    void handsOutAFreshInstancePerCall() {
        assertThat(appConfig.modelMapper()).isNotSameAs(appConfig.modelMapper());
    }
}

