package com.strikerkk.aicommerce.agent_service.auth;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.stereotype.Component;

@Component
public class FeignClientInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate requestTemplate) {
        String userId = UserContext.getUserId();
        String userRole = UserContext.getUserRole();

        if(userId != null) {
            requestTemplate.header("X-user-id", userId);
        }
        if(userRole != null) {
            requestTemplate.header("X-user-role", userRole);
        }
    }
}
