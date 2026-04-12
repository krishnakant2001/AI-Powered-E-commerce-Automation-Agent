package com.strikerkk.aicommerce.user_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class AddressRequest {

    private String houseNo;

    @NotBlank(message = "Street Address name is required")
    private String street;

    @NotBlank(message = "City name is required")
    private String city;

    @NotBlank(message = "State name is required")
    private String state;

    @NotBlank(message = "Country name is required")
    private String country;

    @NotBlank(message = "Pin-code is required")
    @Pattern(regexp = "^[1-9][0-9]{5}$", message = "Pin-code must be at least 6 digits")
    private String pinCode;


    private Boolean isDefault;
}
