package com.strikerkk.aicommerce.payment_service.auth;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.stereotype.Component;

@Component
public class FeignClientInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate requestTemplate) {
        String userId = UserContext.getUserId();

        if(userId != null) {
            requestTemplate.header("X-user-id", userId);
        }
    }
}
