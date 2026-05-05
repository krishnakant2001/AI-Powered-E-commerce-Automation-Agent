package com.strikerkk.aicommerce.agent_service.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class UserInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        String userId = request.getHeader("X-user-id");
        String userRole = request.getHeader("X-user-role");
        String userEmail = request.getHeader("X-user-email");

        if(userId != null) {
            UserContext.setUserId(userId);
        }

        if(userRole != null) {
            UserContext.setUserRole(userRole);
        }

        if(userEmail != null) {
            UserContext.setUserEmail(userEmail);
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserContext.clear();
    }
}
