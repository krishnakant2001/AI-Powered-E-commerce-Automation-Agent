package com.strikerkk.aicommerce.order_service.dto.ClientResponse;

import lombok.Data;

@Data
public class AddressResponse {
    private String houseNo;
    private String street;
    private String city;
    private String state;
    private String country;
    private String pinCode;
    private Boolean isDefault;
}
