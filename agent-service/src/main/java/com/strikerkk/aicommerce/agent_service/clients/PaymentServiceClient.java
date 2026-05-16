package com.strikerkk.aicommerce.agent_service.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "payment-service", path = "/payments")
public interface PaymentServiceClient {

    @PostMapping(value = "/initiate", consumes = "application/json")
    String initiatePayment(@RequestBody String requestBody);


}
