package com.strikerkk.aicommerce.user_service.security;

import com.strikerkk.aicommerce.user_service.common.PageResponse;
import com.strikerkk.aicommerce.user_service.controller.AuthController;
import com.strikerkk.aicommerce.user_service.dto.response.UserResponse;
import com.strikerkk.aicommerce.user_service.service.AuthService;
import com.strikerkk.aicommerce.user_service.service.UserService;
import com.strikerkk.aicommerce.user_service.support.TestDataFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration
@DisplayName("AuthController method security")
class AuthControllerMethodSecurityTest {

    @Configuration
    @EnableMethodSecurity(securedEnabled = true)
    static class TestConfig {

        @Bean
        UserService userService() {
            return Mockito.mock(UserService.class);
        }

        @Bean
        AuthService authService() {
            return Mockito.mock(AuthService.class);
        }

        @Bean
        AuthController authController(UserService userService, AuthService authService) {
            return new AuthController(userService, authService);
        }
    }

    @Autowired
    private AuthController authController;

    @Autowired
    private UserService userService;

    @BeforeEach
    void setUp() {
        Mockito.reset(userService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateWithRole(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "1", null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private PageResponse<UserResponse> emptyPage() {
        return new PageResponse<>(
                new PageImpl<UserResponse>(Collections.emptyList(), PageRequest.of(0, 20), 0));
    }

    @Test
    @DisplayName("an ADMIN can list every user")
    void adminCanListEveryUser() {
        authenticateWithRole("ADMIN");
        when(userService.allUserDetails(anyInt(), anyInt())).thenReturn(emptyPage());

        assertThat(authController.allUserDetails(0, 20).getStatusCode().value()).isEqualTo(200);
        verify(userService).allUserDetails(0, 20);
    }

    @Test
    @DisplayName("a USER cannot list every user")
    void userCannotListEveryUser() {
        authenticateWithRole("USER");

        assertThatThrownBy(() -> authController.allUserDetails(0, 20))
                .isInstanceOf(AccessDeniedException.class);

        verify(userService, never()).allUserDetails(anyInt(), anyInt());
    }

    @Test
    @DisplayName("an anonymous caller cannot list every user")
    void anonymousCannotListEveryUser() {
        assertThatThrownBy(() -> authController.allUserDetails(0, 20))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);

        verify(userService, never()).allUserDetails(anyInt(), anyInt());
    }

    @Test
    @DisplayName("an ADMIN can delete a user")
    void adminCanDeleteUser() {
        authenticateWithRole("ADMIN");

        assertThat(authController.deleteUser("7").getStatusCode().value()).isEqualTo(200);
        verify(userService).deleteUser("7");
    }

    @Test
    @DisplayName("a USER cannot delete a user")
    void userCannotDeleteUser() {
        authenticateWithRole("USER");

        assertThatThrownBy(() -> authController.deleteUser("7"))
                .isInstanceOf(AccessDeniedException.class);

        verify(userService, never()).deleteUser(anyString());
    }

    @Test
    @DisplayName("the non administrative endpoints stay reachable for a plain USER")
    void userCanReadItsOwnDetails() {
        authenticateWithRole("USER");
        when(userService.userDetails()).thenReturn(TestDataFactory.userResponse());

        assertThatCode(() -> authController.userDetails()).doesNotThrowAnyException();
        verify(userService).userDetails();
    }
}

