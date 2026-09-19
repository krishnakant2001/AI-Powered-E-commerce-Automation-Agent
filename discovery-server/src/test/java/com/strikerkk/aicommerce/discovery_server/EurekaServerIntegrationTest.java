package com.strikerkk.aicommerce.discovery_server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@DisplayName("Eureka server HTTP endpoints")
class EurekaServerIntegrationTest {

	@LocalServerPort
	private int port;

	private final TestRestTemplate restTemplate = new TestRestTemplate();

	private String url(String path) {
		return "http://localhost:" + port + path;
	}

	@Test
	@DisplayName("the actuator health endpoint reports the server is UP")
	void healthEndpointReportsUp() {
		ResponseEntity<String> response = restTemplate.getForEntity(url("/actuator/health"), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("\"status\":\"UP\"");
	}

	@Test
	@DisplayName("the Eureka dashboard is served as HTML at the root path")
	void dashboardIsAvailable() {
		ResponseEntity<String> response = restTemplate.getForEntity(url("/"), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getHeaders().getContentType()).isNotNull();
		assertThat(response.getHeaders().getContentType().toString()).contains("html");
		assertThat(response.getBody()).isNotBlank();
	}

	@Test
	@DisplayName("the /eureka/apps registry endpoint responds with an empty application list")
	void emptyRegistryIsReturned() {
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(List.of(MediaType.APPLICATION_JSON));

		ResponseEntity<String> response = restTemplate.exchange(
				url("/eureka/apps"), HttpMethod.GET, new HttpEntity<>(headers), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("applications");
	}
}

