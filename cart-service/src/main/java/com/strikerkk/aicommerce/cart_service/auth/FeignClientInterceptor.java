package com.strikerkk.aicommerce.cart_service.auth;

import feign.RequestInterceptor;
import feign.RequestTemplate;

public class FeignClientInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate requestTemplate) {
        String userId = UserContext.getUserId();

        if(userId != null) {
            requestTemplate.header("X-user-id", userId);
        }
    }
}
