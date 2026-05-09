package com.strikerkk.aicommerce.agent_service.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "user-service", path = "/users")
public interface UserServiceClient {

    @GetMapping("/address/all")
    String getAllAddresses();

    @GetMapping("/address/{addressId}")
    String getAddressByAddressId(@RequestBody Long addressId);
}
