package com.strikerkk.aicommerce.user_service.service;

import com.strikerkk.aicommerce.user_service.dto.request.LoginUserRequest;
import com.strikerkk.aicommerce.user_service.dto.response.AuthResponse;
import com.strikerkk.aicommerce.user_service.dto.response.UserResponse;
import com.strikerkk.aicommerce.user_service.entity.User;
import com.strikerkk.aicommerce.user_service.security.model.CustomUserDetails;
import com.strikerkk.aicommerce.user_service.security.service.JwtService;
import com.strikerkk.aicommerce.user_service.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private ModelMapper modelMapper;

    @InjectMocks
    private AuthService authService;

    @Captor
    private ArgumentCaptor<Authentication> authenticationCaptor;

    private LoginUserRequest request;
    private User user;
    private UserResponse userResponse;

    @BeforeEach
    void setUp() {
        request = TestDataFactory.loginUserRequest();
        user = TestDataFactory.user();
        userResponse = TestDataFactory.userResponse();
    }

    @Test
    @DisplayName("returns a Bearer token together with the user payload on a successful login")
    void shouldReturnAuthResponseOnSuccessfulLogin() {
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null);

        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(authentication);
        when(jwtService.generateAccessToken(user)).thenReturn("generated.jwt.token");
        when(modelMapper.map(user, UserResponse.class)).thenReturn(userResponse);

        AuthResponse response = authService.loginUser(request);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("generated.jwt.token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getUser()).isSameAs(userResponse);
    }

    @Test
    @DisplayName("authenticates with the exact email and password taken from the request")
    void shouldAuthenticateWithRequestCredentials() {
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null);

        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(authentication);
        when(jwtService.generateAccessToken(user)).thenReturn("generated.jwt.token");
        when(modelMapper.map(user, UserResponse.class)).thenReturn(userResponse);

        authService.loginUser(request);

        verify(authenticationManager).authenticate(authenticationCaptor.capture());
        Authentication submitted = authenticationCaptor.getValue();
        assertThat(submitted).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        assertThat(submitted.getPrincipal()).isEqualTo(TestDataFactory.EMAIL);
        assertThat(submitted.getCredentials()).isEqualTo(TestDataFactory.RAW_PASSWORD);
        assertThat(submitted.isAuthenticated()).isFalse();
    }

    @Test
    @DisplayName("propagates BadCredentialsException and never issues a token")
    void shouldPropagateBadCredentialsException() {
        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.loginUser(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Bad credentials");

        verifyNoInteractions(jwtService);
        verifyNoInteractions(modelMapper);
    }

    @Test
    @DisplayName("propagates any other AuthenticationException raised by the AuthenticationManager")
    void shouldPropagateOtherAuthenticationExceptions() {
        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenThrow(new DisabledException("User is disabled"));

        assertThatThrownBy(() -> authService.loginUser(request))
                .isInstanceOf(DisabledException.class)
                .hasMessage("User is disabled");

        verifyNoInteractions(jwtService);
    }
}

