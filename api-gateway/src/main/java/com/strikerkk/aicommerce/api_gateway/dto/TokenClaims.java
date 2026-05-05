package com.strikerkk.aicommerce.api_gateway.dto;

import lombok.Data;

@Data
public class TokenClaims {
    private final String userId;
    private final String role;
    private final String email;
}
