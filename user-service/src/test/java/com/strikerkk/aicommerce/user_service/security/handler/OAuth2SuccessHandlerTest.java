package com.strikerkk.aicommerce.user_service.security.handler;

import com.strikerkk.aicommerce.user_service.entity.User;
import com.strikerkk.aicommerce.user_service.repository.UserRepository;
import com.strikerkk.aicommerce.user_service.security.service.JwtService;
import com.strikerkk.aicommerce.user_service.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OAuth2SuccessHandler")
class OAuth2SuccessHandlerTest {

    private static final String GOOGLE_SUB = "google-sub-123";
    private static final String GOOGLE_EMAIL = "krishna@gmail.com";
    private static final String JWT_TOKEN = "generated.jwt.token";
    private static final String REDIRECT_PREFIX = "http://localhost:8080/users/oauth2/success?token=";

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private OAuth2AuthorizedClientService authorizedClientService;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    private OAuth2SuccessHandler handler;
    private OAuth2AuthenticationToken authenticationToken;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        handler = new OAuth2SuccessHandler(userRepository, jwtService);
        ReflectionTestUtils.setField(handler, "authorizedClientService", authorizedClientService);

        OidcIdToken idToken = new OidcIdToken(
                "id-token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("sub", GOOGLE_SUB));

        OidcUser oidcUser = org.mockito.Mockito.mock(OidcUser.class);
        when(oidcUser.getIdToken()).thenReturn(idToken);
        when(oidcUser.getSubject()).thenReturn(GOOGLE_SUB);
        when(oidcUser.getEmail()).thenReturn(GOOGLE_EMAIL);
        when(oidcUser.getGivenName()).thenReturn("Krishnakant");
        when(oidcUser.getFamilyName()).thenReturn("Nagvanshi");
        when(oidcUser.getName()).thenReturn(GOOGLE_SUB);

        authenticationToken = new OAuth2AuthenticationToken(
                oidcUser, List.of(new SimpleGrantedAuthority("ROLE_USER")), "google");

        OAuth2AuthorizedClient authorizedClient = org.mockito.Mockito.mock(OAuth2AuthorizedClient.class);
        when(authorizedClient.getAccessToken()).thenReturn(new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "google-access-token",
                Instant.now(),
                Instant.now().plusSeconds(3600)));
        doReturn(authorizedClient)
                .when(authorizedClientService).loadAuthorizedClient(anyString(), anyString());

        when(jwtService.generateAccessToken(any(User.class))).thenReturn(JWT_TOKEN);

        request = new MockHttpServletRequest("GET", "/login/oauth2/code/google");
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("reuses the account already linked to the Google id")
    void shouldReuseAccountLinkedToGoogleId() throws Exception {
        User existing = TestDataFactory.user();
        existing.setGoogleId(GOOGLE_SUB);
        when(userRepository.findByGoogleId(GOOGLE_SUB)).thenReturn(Optional.of(existing));

        handler.onAuthenticationSuccess(request, response, authenticationToken);

        verify(userRepository, never()).findByEmail(anyString());
        verify(userRepository, never()).save(any(User.class));
        verify(jwtService).generateAccessToken(existing);
        assertThat(response.getRedirectedUrl()).isEqualTo(REDIRECT_PREFIX + JWT_TOKEN);
    }

    @Test
    @DisplayName("links the Google id to an existing account that uses the same email")
    void shouldLinkGoogleIdToExistingEmailAccount() throws Exception {
        User existing = TestDataFactory.user();
        existing.setEmail(GOOGLE_EMAIL);
        existing.setGoogleId(null);

        when(userRepository.findByGoogleId(GOOGLE_SUB)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(GOOGLE_EMAIL)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        handler.onAuthenticationSuccess(request, response, authenticationToken);

        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertThat(saved.getId()).isEqualTo(TestDataFactory.USER_ID);
        assertThat(saved.getGoogleId()).isEqualTo(GOOGLE_SUB);
        assertThat(saved.getEmail()).isEqualTo(GOOGLE_EMAIL);
        assertThat(response.getRedirectedUrl()).isEqualTo(REDIRECT_PREFIX + JWT_TOKEN);
    }

    @Test
    @DisplayName("creates a brand new account for a first time Google user")
    void shouldCreateNewAccountForFirstTimeGoogleUser() throws Exception {
        when(userRepository.findByGoogleId(GOOGLE_SUB)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(GOOGLE_EMAIL)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User toSave = inv.getArgument(0);
            toSave.setId(99L);
            return toSave;
        });

        handler.onAuthenticationSuccess(request, response, authenticationToken);

        verify(userRepository).save(userCaptor.capture());
        User created = userCaptor.getValue();
        assertThat(created.getGoogleId()).isEqualTo(GOOGLE_SUB);
        assertThat(created.getEmail()).isEqualTo(GOOGLE_EMAIL);
        assertThat(created.getFirstName()).isEqualTo("Krishnakant");
        assertThat(created.getLastName()).isEqualTo("Nagvanshi");
        assertThat(created.getPassword()).isNotBlank();
        assertThat(created.getRole()).isEqualTo(com.strikerkk.aicommerce.user_service.entity.enums.Role.USER);
        assertThat(response.getRedirectedUrl()).isEqualTo(REDIRECT_PREFIX + JWT_TOKEN);
    }

    @Test
    @DisplayName("generates a random, non guessable password for a Google-only account")
    void shouldGenerateRandomPasswordForGoogleOnlyAccount() throws Exception {
        when(userRepository.findByGoogleId(GOOGLE_SUB)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(GOOGLE_EMAIL)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        handler.onAuthenticationSuccess(request, response, authenticationToken);

        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPassword())
                .isNotNull()
                .hasSize(36)          // UUID representation
                .isNotEqualTo(GOOGLE_SUB);
    }

    @Test
    @DisplayName("loads the authorized client of the registration that performed the login")
    void shouldLoadAuthorizedClientForGoogleRegistration() throws Exception {
        when(userRepository.findByGoogleId(GOOGLE_SUB)).thenReturn(Optional.of(TestDataFactory.user()));

        handler.onAuthenticationSuccess(request, response, authenticationToken);

        verify(authorizedClientService).loadAuthorizedClient("google", GOOGLE_SUB);
    }
}

