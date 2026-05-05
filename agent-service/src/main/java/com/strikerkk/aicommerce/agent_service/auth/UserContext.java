package com.strikerkk.aicommerce.agent_service.auth;

public class UserContext {

    private static final ThreadLocal<String> userId = new ThreadLocal<>();
    private static final ThreadLocal<String> userRole = new ThreadLocal<>();
    private static final ThreadLocal<String> userEmail = new ThreadLocal<>();

    public static void setUserId(String id) {
        userId.set(id);
    }

    public static String getUserId() {
        return userId.get();
    }

    public static void setUserRole(String role) {
        userRole.set(role);
    }

    public static String getUserRole() {
        return userRole.get();
    }

    public static void setUserEmail(String email) {
        userEmail.set(email);
    }

    public static String getUserEmail() {
        return userEmail.get();
    }

    public static void clear() {
        userId.remove();
    }
}
