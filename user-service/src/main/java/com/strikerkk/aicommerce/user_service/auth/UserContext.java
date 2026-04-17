package com.strikerkk.aicommerce.user_service.auth;

public class UserContext {

    private static final ThreadLocal<String> userId = new ThreadLocal<>();
    private static final ThreadLocal<String> userRole = new ThreadLocal<>();

    public static void setUserId(String id) {
        userId.set(id);
    }

    public static void setUserRole(String role) {
        userRole.set(role);
    }

    public static String getUserId() {
        return userId.get();
    }

    public static String getUserRole() {
        return userRole.get();
    }

    // Always clear after request to avoid memory leaks
    public static void clear() {
        userId.remove();
        userRole.remove();
    }
}
