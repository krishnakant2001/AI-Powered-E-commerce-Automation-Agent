package com.strikerkk.aicommerce.product_service;

import com.strikerkk.aicommerce.product_service.auth.FeignClientInterceptor;
import com.strikerkk.aicommerce.product_service.auth.UserInterceptor;
import com.strikerkk.aicommerce.product_service.auth.WebConfig;
import com.strikerkk.aicommerce.product_service.consumer.OrderConfirmedConsumer;
import com.strikerkk.aicommerce.product_service.controller.ProductAdminController;
import com.strikerkk.aicommerce.product_service.controller.ProductController;
import com.strikerkk.aicommerce.product_service.exception.advice.GlobalExceptionHandler;
import com.strikerkk.aicommerce.product_service.helper.ProductOwnershipValidator;
import com.strikerkk.aicommerce.product_service.repository.ProductImageRepository;
import com.strikerkk.aicommerce.product_service.repository.ProductRepository;
import com.strikerkk.aicommerce.product_service.repository.ProductVariantRepository;
import com.strikerkk.aicommerce.product_service.service.ProductCartService;
import com.strikerkk.aicommerce.product_service.service.ProductImageService;
import com.strikerkk.aicommerce.product_service.service.ProductService;
import com.strikerkk.aicommerce.product_service.service.ProductVariantService;
import com.strikerkk.aicommerce.product_service.service.S3ImageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.services.s3.S3Client;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("ProductServiceApplication")
class ProductServiceApplicationTests {

	@Autowired
	private ApplicationContext applicationContext;

	/** Never build a real AWS client from a test. */
	@MockitoBean
	private S3Client s3Client;

	@Test
	@DisplayName("the application context starts")
	void contextLoads() {
		assertThat(applicationContext).isNotNull();
	}

	@Test
	@DisplayName("every controller is registered")
	void controllersAreRegistered() {
		assertThat(applicationContext.getBean(ProductController.class)).isNotNull();
		assertThat(applicationContext.getBean(ProductAdminController.class)).isNotNull();
	}

	@Test
	@DisplayName("every service is registered")
	void servicesAreRegistered() {
		assertThat(applicationContext.getBean(ProductService.class)).isNotNull();
		assertThat(applicationContext.getBean(ProductVariantService.class)).isNotNull();
		assertThat(applicationContext.getBean(ProductImageService.class)).isNotNull();
		assertThat(applicationContext.getBean(ProductCartService.class)).isNotNull();
		assertThat(applicationContext.getBean(S3ImageService.class)).isNotNull();
	}

	@Test
	@DisplayName("every repository is registered")
	void repositoriesAreRegistered() {
		assertThat(applicationContext.getBean(ProductRepository.class)).isNotNull();
		assertThat(applicationContext.getBean(ProductVariantRepository.class)).isNotNull();
		assertThat(applicationContext.getBean(ProductImageRepository.class)).isNotNull();
	}

	@Test
	@DisplayName("the security and the gateway-header infrastructure is registered")
	void securityInfrastructureIsRegistered() {
		assertThat(applicationContext.getBean(SecurityFilterChain.class)).isNotNull();
		assertThat(applicationContext.getBean(UserInterceptor.class)).isNotNull();
		assertThat(applicationContext.getBean(WebConfig.class)).isNotNull();
		assertThat(applicationContext.getBean(FeignClientInterceptor.class)).isNotNull();
		assertThat(applicationContext.getBean(ProductOwnershipValidator.class)).isNotNull();
		assertThat(applicationContext.getBean(GlobalExceptionHandler.class)).isNotNull();
	}

	@Test
	@DisplayName("the kafka consumer is registered")
	void kafkaConsumerIsRegistered() {
		assertThat(applicationContext.getBean(OrderConfirmedConsumer.class)).isNotNull();
	}

	@Test
	@DisplayName("a single ModelMapper is shared by every service")
	void aSingleModelMapperIsShared() {
		assertThat(applicationContext.getBeansOfType(ModelMapper.class)).hasSize(1);
	}
}
