package com.strikerkk.aicommerce.user_service.dto.response;

import lombok.Data;

@Data
public class AddressResponse {
    private Long id;
    private String houseNo;
    private String street;
    private String city;
    private String state;
    private String country;
    private String pinCode;
    private Boolean isDefault;
}
