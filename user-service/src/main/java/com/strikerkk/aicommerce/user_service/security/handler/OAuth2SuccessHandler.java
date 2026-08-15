package com.strikerkk.aicommerce.user_service.security.handler;

import com.strikerkk.aicommerce.user_service.entity.User;
import com.strikerkk.aicommerce.user_service.repository.UserRepository;
import com.strikerkk.aicommerce.user_service.security.service.JwtService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    @Autowired
    private OAuth2AuthorizedClientService authorizedClientService;

    private final UserRepository userRepository;
    private final JwtService jwtService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        OAuth2AuthenticationToken authToken = (OAuth2AuthenticationToken) authentication;
        OidcUser oidcUser = (OidcUser) authToken.getPrincipal();
        OidcIdToken idToken = oidcUser.getIdToken();

        String oidcIdToken = idToken.getTokenValue();
        log.info("OAuth2.0 idToken: {}", oidcIdToken);

        String userEmail = oidcUser.getEmail();

        User user = userRepository.findByEmail(userEmail)
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .firstName(oidcUser.getGivenName())
                            .lastName(oidcUser.getFamilyName())
                            .email(oidcUser.getEmail())
                            .password(oidcUser.getAccessTokenHash()+"google")
                            .build();

                    return userRepository.save(newUser);
                });

        String jwtToken = jwtService.generateAccessToken(user);


        OAuth2AuthorizedClient client = authorizedClientService
                .loadAuthorizedClient(authToken.getAuthorizedClientRegistrationId(), authToken.getName());

        String accessToken = client.getAccessToken().getTokenValue();
        log.info("ACCESS TOKEN: {}", accessToken);

        String frontEndUrl = "http://localhost:8080/users/oauth2/success?token=";
        getRedirectStrategy().sendRedirect(request, response, frontEndUrl + jwtToken);
    }
}
