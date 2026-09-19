package com.strikerkk.aicommerce.discovery_server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DiscoveryServerApplication metadata")
class DiscoveryServerApplicationMetadataTest {

	@Test
	@DisplayName("is annotated with @SpringBootApplication")
	void isSpringBootApplication() {
		assertThat(DiscoveryServerApplication.class.isAnnotationPresent(SpringBootApplication.class)).isTrue();
	}

	@Test
	@DisplayName("is annotated with @EnableEurekaServer so it acts as a service registry")
	void enablesEurekaServer() {
		assertThat(DiscoveryServerApplication.class.isAnnotationPresent(EnableEurekaServer.class)).isTrue();
	}

	@Test
	@DisplayName("exposes a standard public static void main(String[]) entry point")
	void hasStandardMainMethod() throws NoSuchMethodException {
		Method main = DiscoveryServerApplication.class.getMethod("main", String[].class);

		assertThat(Modifier.isPublic(main.getModifiers())).isTrue();
		assertThat(Modifier.isStatic(main.getModifiers())).isTrue();
		assertThat(main.getReturnType()).isEqualTo(void.class);
	}
}

