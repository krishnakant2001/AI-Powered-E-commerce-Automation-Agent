package com.strikerkk.aicommerce.user_service;

import com.strikerkk.aicommerce.user_service.controller.AddressController;
import com.strikerkk.aicommerce.user_service.controller.AuthController;
import com.strikerkk.aicommerce.user_service.controller.AuthPageController;
import com.strikerkk.aicommerce.user_service.exception.advice.GlobalExceptionHandler;
import com.strikerkk.aicommerce.user_service.repository.AddressRepository;
import com.strikerkk.aicommerce.user_service.repository.UserRepository;
import com.strikerkk.aicommerce.user_service.security.handler.CustomAccessDeniedHandler;
import com.strikerkk.aicommerce.user_service.security.handler.CustomAuthenticationEntryPoint;
import com.strikerkk.aicommerce.user_service.security.handler.OAuth2SuccessHandler;
import com.strikerkk.aicommerce.user_service.security.service.JwtService;
import com.strikerkk.aicommerce.user_service.service.AddressService;
import com.strikerkk.aicommerce.user_service.service.AuthService;
import com.strikerkk.aicommerce.user_service.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("UserServiceApplication")
class UserServiceApplicationTests {

	@Autowired
	private ApplicationContext applicationContext;

	@Test
	@DisplayName("the application context starts")
	void contextLoads() {
		assertThat(applicationContext).isNotNull();
	}

	@Test
	@DisplayName("every controller is registered")
	void controllersAreRegistered() {
		assertThat(applicationContext.getBean(AuthController.class)).isNotNull();
		assertThat(applicationContext.getBean(AddressController.class)).isNotNull();
		assertThat(applicationContext.getBean(AuthPageController.class)).isNotNull();
	}

	@Test
	@DisplayName("every service is registered")
	void servicesAreRegistered() {
		assertThat(applicationContext.getBean(UserService.class)).isNotNull();
		assertThat(applicationContext.getBean(AuthService.class)).isNotNull();
		assertThat(applicationContext.getBean(AddressService.class)).isNotNull();
		assertThat(applicationContext.getBean(JwtService.class)).isNotNull();
	}

	@Test
	@DisplayName("every repository is registered")
	void repositoriesAreRegistered() {
		assertThat(applicationContext.getBean(UserRepository.class)).isNotNull();
		assertThat(applicationContext.getBean(AddressRepository.class)).isNotNull();
	}

	@Test
	@DisplayName("the security infrastructure is registered")
	void securityInfrastructureIsRegistered() {
		assertThat(applicationContext.getBean(SecurityFilterChain.class)).isNotNull();
		assertThat(applicationContext.getBean(AuthenticationManager.class)).isNotNull();
		assertThat(applicationContext.getBean(OAuth2SuccessHandler.class)).isNotNull();
		assertThat(applicationContext.getBean(CustomAuthenticationEntryPoint.class)).isNotNull();
		assertThat(applicationContext.getBean(CustomAccessDeniedHandler.class)).isNotNull();
		assertThat(applicationContext.getBean(GlobalExceptionHandler.class)).isNotNull();
	}

	@Test
	@DisplayName("passwords are hashed with BCrypt")
	void passwordEncoderIsBCrypt() {
		assertThat(applicationContext.getBean(PasswordEncoder.class))
				.isInstanceOf(BCryptPasswordEncoder.class);
	}
}
