package com.strikerkk.aicommerce.agent_service.config;

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
    @DisplayName("is a Spring configuration class")
    void isAConfiguration() {
        assertThat(AppConfig.class.getAnnotation(Configuration.class)).isNotNull();
    }

    @Test
    @DisplayName("exposes the ModelMapper as a bean")
    void exposesTheModelMapperAsABean() throws Exception {
        assertThat(AppConfig.class.getDeclaredMethod("modelMapper").getAnnotation(Bean.class)).isNotNull();
    }

    @Test
    @DisplayName("hands back a usable ModelMapper")
    void handsBackAUsableModelMapper() {
        ModelMapper modelMapper = appConfig.modelMapper();

        assertThat(modelMapper).isNotNull();
        assertThat(modelMapper.getConfiguration()).isNotNull();
    }

    @Test
    @DisplayName("builds a new instance on every call - Spring is what makes it a singleton")
    void buildsANewInstanceOnEveryCall() {
        assertThat(appConfig.modelMapper()).isNotSameAs(appConfig.modelMapper());
    }
}

