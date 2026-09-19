package com.strikerkk.aicommerce.discovery_server;

import com.netflix.eureka.EurekaServerContext;
import com.netflix.eureka.registry.PeerAwareInstanceRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("DiscoveryServerApplication")
class DiscoveryServerApplicationTests {

	@Autowired
	private ApplicationContext applicationContext;

	@Value("${eureka.client.register-with-eureka}")
	private boolean registerWithEureka;

	@Value("${eureka.client.fetch-registry}")
	private boolean fetchRegistry;

	@Test
	@DisplayName("the application context starts")
	void contextLoads() {
		assertThat(applicationContext).isNotNull();
	}

	@Test
	@DisplayName("the Eureka server infrastructure is wired up")
	void eurekaServerBeansAreRegistered() {
		assertThat(applicationContext.getBean(EurekaServerContext.class)).isNotNull();
		assertThat(applicationContext.getBean(PeerAwareInstanceRegistry.class)).isNotNull();
	}

	@Test
	@DisplayName("this node never registers itself with, nor fetches a registry from, another instance")
	void isAStandaloneRegistry() {
		assertThat(registerWithEureka).isFalse();
		assertThat(fetchRegistry).isFalse();
	}

}
