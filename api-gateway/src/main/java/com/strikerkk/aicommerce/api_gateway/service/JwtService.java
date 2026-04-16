package com.strikerkk.aicommerce.api_gateway.service;

import com.strikerkk.aicommerce.api_gateway.dto.TokenClaims;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Service
public class JwtService {

    @Value("${jwt.secretKey}")
    private String jwtSecretKey;

    private SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(jwtSecretKey.getBytes(StandardCharsets.UTF_8));
    }

    public TokenClaims getClaimsFromToken(String authToken) {
        Claims claims = Jwts.parser()
                .verifyWith(getSecretKey())
                .build()
                .parseSignedClaims(authToken)
                .getPayload();

        return new TokenClaims(
                claims.getSubject(),
                claims.get("role", String.class)
        );
    }

//    public String getRoleFromToken(String authToken) {
//        Claims claims = Jwts.parser()
//                .verifyWith(getSecretKey())
//                .build()
//                .parseSignedClaims(authToken)
//                .getPayload();
//
//        return claims.get("role", String.class);
//    }

}
