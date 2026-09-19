package com.strikerkk.aicommerce.api_gateway;

import com.strikerkk.aicommerce.api_gateway.filters.AuthenticationFilter;
import com.strikerkk.aicommerce.api_gateway.service.JwtService;
import com.strikerkk.aicommerce.api_gateway.support.TestTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest(properties = {
		TestTokens.JWT_SECRET_PROPERTY,
		TestTokens.EUREKA_DISABLED_PROPERTY,
		TestTokens.EUREKA_NO_REGISTER_PROPERTY,
		TestTokens.EUREKA_NO_FETCH_PROPERTY
})
@DisplayName("ApiGatewayApplication context")
class ApiGatewayApplicationTests {

	@Autowired
	private ApplicationContext context;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private AuthenticationFilter authenticationFilter;

	@Autowired
	private RouteLocator routeLocator;

	@Test
	@DisplayName("loads")
	void contextLoads() {
		assertThat(context).isNotNull();
		assertThat(context.getEnvironment().getProperty("spring.application.name"))
				.isEqualTo("api-gateway");
	}

	@Test
	@DisplayName("registers the JwtService and the AuthenticationFilter as singletons")
	void shouldRegisterTheGatewayBeans() {
		assertThat(jwtService).isNotNull();
		assertThat(authenticationFilter).isNotNull();
		assertThat(context.getBean(JwtService.class)).isSameAs(jwtService);
		assertThat(context.getBean(AuthenticationFilter.class)).isSameAs(authenticationFilter);
	}

	@Test
	@DisplayName("injects jwt.secretKey into the JwtService")
	void shouldInjectTheConfiguredSecret() {
		assertThat(ReflectionTestUtils.getField(jwtService, "jwtSecretKey"))
				.isEqualTo(TestTokens.SECRET);
	}

	@Test
	@DisplayName("publishes the AuthenticationFilter under the name used by application.yml")
	void shouldPublishTheFilterUnderTheYamlName() {
		assertThat(gatewayFilterFactoryNames())
				.as("a route referencing an unknown filter name fails the whole gateway at startup")
				.contains("AuthenticationFilter");
	}

	@Test
	@DisplayName("wires the built-in StripPrefix and PreserveHostHeader filters used by the routes")
	void shouldWireTheBuiltInFiltersUsedByTheRoutes() {
		assertThat(gatewayFilterFactoryNames())
				.contains("StripPrefix", "PreserveHostHeader", "AuthenticationFilter");
	}

	@Test
	@DisplayName("builds the routing table at startup")
	void shouldBuildTheRoutingTable() {
		assertThat(routeLocator.getRoutes().collectList().block())
				.isNotNull()
				.hasSize(7);
	}

	@SuppressWarnings("rawtypes")
	private List<String> gatewayFilterFactoryNames() {
		Map<String, GatewayFilterFactory> factories = context.getBeansOfType(GatewayFilterFactory.class);
		return factories.values().stream().map(GatewayFilterFactory::name).toList();
	}
}
