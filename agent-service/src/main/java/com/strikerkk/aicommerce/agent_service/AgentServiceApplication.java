package com.strikerkk.aicommerce.agent_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class AgentServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AgentServiceApplication.class, args);
	}

}
