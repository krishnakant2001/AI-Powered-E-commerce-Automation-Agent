package com.strikerkk.aicommerce.order_service.clients;

import com.strikerkk.aicommerce.order_service.dto.ClientResponse.AddressResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user-service")
public interface UserClient {

    @GetMapping("/users/address/{addressId}")
    AddressResponse getAddressByAddressId(@PathVariable Long addressId);
}
