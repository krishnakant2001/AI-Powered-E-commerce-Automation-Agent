package com.strikerkk.aicommerce.user_service.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserResponse {
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private String role;
    private List<AddressResponse> addresses;
    private LocalDateTime createdAt;
}
