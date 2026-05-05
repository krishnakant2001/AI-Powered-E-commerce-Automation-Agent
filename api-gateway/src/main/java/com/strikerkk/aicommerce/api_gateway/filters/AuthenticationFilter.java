package com.strikerkk.aicommerce.api_gateway.filters;

import com.strikerkk.aicommerce.api_gateway.dto.TokenClaims;
import com.strikerkk.aicommerce.api_gateway.service.JwtService;
import jakarta.ws.rs.core.HttpHeaders;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;

import java.util.List;

@Slf4j
@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

    private final JwtService jwtService;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();


    // Paths that skip JWT validation
    private static final List<String> PUBLIC_PATHS = List.of(
            "/users/auth/signup",
            "/users/auth/login",
            "/payments/webhook/razorpay",
            "/payments/page/**"
    );

    public AuthenticationFilter(JwtService jwtService) {
        super(Config.class);
        this.jwtService = jwtService;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return ((exchange, chain) -> {

            final String path = exchange.getRequest().getURI().getPath();

            // Skip auth for public paths
            if (PUBLIC_PATHS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path))) {
                return chain.filter(exchange);
            }
//            if(PUBLIC_PATHS.contains(path)) {
//                return chain.filter(exchange);
//            }


            if(!exchange.getRequest().getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            final String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            if(authHeader == null || !authHeader.startsWith("Bearer ")) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            final String token = authHeader.substring(7);
            final String authToken = authHeader.split("Bearer ")[1];

            try {
                TokenClaims tokenClaims = jwtService.getClaimsFromToken(authToken);

                ServerWebExchange modifiedExchange = exchange
                        .mutate()
                        .request(req -> req
                                .header("X-user-id", tokenClaims.getUserId())
                                .header("X-user-role", tokenClaims.getRole())
                                .header("X-user-email", tokenClaims.getEmail())
                        )
                        .build();

                return chain.filter(modifiedExchange);

            } catch (RuntimeException e) {
                log.info("JWT validation failed: {}", e.getMessage());
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }
        });
    }

    public static class Config {

    }


}
