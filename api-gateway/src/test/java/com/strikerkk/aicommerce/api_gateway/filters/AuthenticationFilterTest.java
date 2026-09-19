package com.strikerkk.aicommerce.api_gateway.filters;

import com.strikerkk.aicommerce.api_gateway.dto.TokenClaims;
import com.strikerkk.aicommerce.api_gateway.service.JwtService;
import com.strikerkk.aicommerce.api_gateway.support.TestTokens;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthenticationFilter")
class AuthenticationFilterTest {

    private static final String BASE_URL = "http://gateway.local";
    private static final String PROTECTED_PATH = "/products/1";

    @Mock
    private JwtService jwtService;

    @Mock
    private GatewayFilterChain chain;

    private AuthenticationFilter filterFactory;
    private GatewayFilter filter;

    @BeforeEach
    void setUp() {
        filterFactory = new AuthenticationFilter(jwtService);
        filter = filterFactory.apply(new AuthenticationFilter.Config());
    }

    // helpers ------------------------------------------------------------------

    private static MockServerWebExchange exchangeFor(String path, String authorizationHeader) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.get(BASE_URL + path);
        if (authorizationHeader != null) {
            builder = builder.header(HttpHeaders.AUTHORIZATION, authorizationHeader);
        }
        return MockServerWebExchange.from(builder.build());
    }

    private void chainCompletes() {
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    private ServerWebExchange captureForwardedExchange() {
        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        return captor.getValue();
    }

    private static void assertUnauthorized(MockServerWebExchange exchange, Mono<Void> result) {
        StepVerifier.create(result).verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // factory contract ------------------------------------------------------------------

    @Nested
    @DisplayName("GatewayFilterFactory contract")
    class FactoryContract {

        @Test
        @DisplayName("is published under the name 'AuthenticationFilter' used in application.yml")
        void shouldBeRegisteredUnderTheNameUsedInYaml() {
            assertThat(filterFactory.name()).isEqualTo("AuthenticationFilter");
        }

        @Test
        @DisplayName("declares AuthenticationFilter.Config as its configuration class")
        void shouldDeclareItsConfigClass() {
            assertThat(filterFactory.getConfigClass()).isEqualTo(AuthenticationFilter.Config.class);
        }

        @Test
        @DisplayName("can build a configuration instance for the no-argument yaml shortcut")
        void shouldBuildConfigForShortcutSyntax() {
            assertThat(filterFactory.newConfig()).isInstanceOf(AuthenticationFilter.Config.class);
        }

        @Test
        @DisplayName("apply() hands out a usable filter every time it is called")
        void shouldReturnAFilterOnEveryCall() {
            assertThat(filterFactory.apply(new AuthenticationFilter.Config())).isNotNull();
            assertThat(filterFactory.apply(new AuthenticationFilter.Config())).isNotNull();
        }

        @Test
        @DisplayName("the jakarta.ws.rs header constant it imports is the standard 'Authorization'")
        void jakartaAuthorizationConstantMatchesTheSpringOne() {
            // AuthenticationFilter imports jakarta.ws.rs.core.HttpHeaders (pulled in transitively by
            // the Eureka client) instead of org.springframework.http.HttpHeaders. Both constants must
            // stay identical, otherwise the filter would look at the wrong header.
            assertThat(jakarta.ws.rs.core.HttpHeaders.AUTHORIZATION)
                    .isEqualTo(HttpHeaders.AUTHORIZATION)
                    .isEqualTo("Authorization");
        }
    }

    // missing / malformed header ------------------------------------------------------------------

    @Nested
    @DisplayName("when the Authorization header is missing or malformed")
    class MissingOrMalformedHeader {

        @Test
        @DisplayName("answers 401 and never calls the downstream chain when the header is absent")
        void shouldRejectRequestWithoutAuthorizationHeader() {
            MockServerWebExchange exchange = exchangeFor(PROTECTED_PATH, null);

            assertUnauthorized(exchange, filter.filter(exchange, chain));

            verify(chain, never()).filter(any());
            verifyNoInteractions(jwtService);
        }

        @ParameterizedTest(name = "[{index}] Authorization: \"{0}\"")
        @ValueSource(strings = {
                "",
                "   ",
                "Bearer",                       // the scheme only, no trailing space, no token
                "Bearer\ttoken",                // tab instead of a space
                "bearer lower-case-scheme",     // the prefix check is case-sensitive
                "BEARER UPPER-CASE-SCHEME",
                "Basic dXNlcjpwYXNz",
                "Token abc.def.ghi",
                "eyJhbGciOiJIUzI1NiJ9.e30.sig"  // raw token without the scheme
        })
        @DisplayName("answers 401 for anything that is not a 'Bearer ' header")
        void shouldRejectNonBearerHeaders(String header) {
            MockServerWebExchange exchange = exchangeFor(PROTECTED_PATH, header);

            assertUnauthorized(exchange, filter.filter(exchange, chain));

            verify(chain, never()).filter(any());
            verifyNoInteractions(jwtService);
        }

        @Test
        @DisplayName("the header name is matched case-insensitively, as HTTP requires")
        void shouldAcceptLowerCaseHeaderName() {
            chainCompletes();
            when(jwtService.getClaimsFromToken(anyString()))
                    .thenReturn(new TokenClaims(TestTokens.USER_ID, TestTokens.ROLE, TestTokens.EMAIL));

            MockServerHttpRequest request = MockServerHttpRequest.get(BASE_URL + PROTECTED_PATH)
                    .header("authorization", TestTokens.bearer("a.b.c"))
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            verify(chain).filter(any());
            assertThat(exchange.getResponse().getStatusCode()).isNull();
        }
    }

    // invalid token ------------------------------------------------------------------

    @Nested
    @DisplayName("when the Bearer token cannot be validated")
    class InvalidToken {

        @Test
        @DisplayName("answers 401 when the signature does not match")
        void shouldRejectBadSignature() {
            when(jwtService.getClaimsFromToken(anyString()))
                    .thenThrow(new SignatureException("bad signature"));

            MockServerWebExchange exchange =
                    exchangeFor(PROTECTED_PATH, TestTokens.bearer(TestTokens.tokenSignedWithAnotherKey()));

            assertUnauthorized(exchange, filter.filter(exchange, chain));
            verify(chain, never()).filter(any());
        }

        @Test
        @DisplayName("answers 401 when the token has expired")
        void shouldRejectExpiredToken() {
            when(jwtService.getClaimsFromToken(anyString()))
                    .thenThrow(new ExpiredJwtException(null, null, "expired"));

            MockServerWebExchange exchange =
                    exchangeFor(PROTECTED_PATH, TestTokens.bearer(TestTokens.expiredToken()));

            assertUnauthorized(exchange, filter.filter(exchange, chain));
            verify(chain, never()).filter(any());
        }

        @Test
        @DisplayName("answers 401 for any other RuntimeException raised by JwtService")
        void shouldRejectOnAnyRuntimeException() {
            when(jwtService.getClaimsFromToken(anyString()))
                    .thenThrow(new RuntimeException("boom"));

            MockServerWebExchange exchange = exchangeFor(PROTECTED_PATH, TestTokens.bearer("a.b.c"));

            assertUnauthorized(exchange, filter.filter(exchange, chain));
            verify(chain, never()).filter(any());
        }

        @Test
        @DisplayName("never leaks the failure reason into the response")
        void shouldNotLeakTheFailureReason() {
            when(jwtService.getClaimsFromToken(anyString()))
                    .thenThrow(new SignatureException("JWT signature does not match locally computed signature"));

            MockServerWebExchange exchange = exchangeFor(PROTECTED_PATH, TestTokens.bearer("a.b.c"));

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(exchange.getResponse().getHeaders().toString())
                    .doesNotContain("signature")
                    .doesNotContain("X-user-id");
            verify(chain, never()).filter(any());
        }

        @Test
        @DisplayName("BUG: 'Bearer ' with an empty token blows up instead of answering 401")
        void shouldDocumentTheEmptyTokenCrash() {
            // AuthenticationFilter does `authHeader.split("Bearer ")[1]` OUTSIDE of its try/catch.
            // For the header "Bearer " the split returns an EMPTY array (Java drops the trailing
            // empty strings), so the line raises an ArrayIndexOutOfBoundsException that escapes the
            // filter instead of producing the intended 401.
            // See src/test/README.md, "Notes / findings" (#1) for the suggested one line fix.
            MockServerWebExchange exchange = exchangeFor(PROTECTED_PATH, "Bearer ");

            assertThatThrownBy(() -> filter.filter(exchange, chain))
                    .isInstanceOf(ArrayIndexOutOfBoundsException.class);

            verify(chain, never()).filter(any());
            verifyNoInteractions(jwtService);
            assertThat(exchange.getResponse().getStatusCode()).isNull();
        }

        @Test
        @DisplayName("BUG: the token is taken with split(), not substring(7)")
        void shouldDocumentTheSplitBasedTokenExtraction() {
            // `final String token = authHeader.substring(7);` is dead code: the filter actually uses
            // `authHeader.split("Bearer ")[1]`. The two disagree as soon as the header contains the
            // literal "Bearer " more than once, and split() silently truncates the token.
            when(jwtService.getClaimsFromToken(anyString()))
                    .thenThrow(new SignatureException("bad signature"));

            MockServerWebExchange exchange = exchangeFor(PROTECTED_PATH, "Bearer aaaBearer bbb");

            assertUnauthorized(exchange, filter.filter(exchange, chain));

            // substring(7) would have produced "aaaBearer bbb"
            verify(jwtService).getClaimsFromToken("aaa");
        }
    }

    // happy path ------------------------------------------------------------------

    @Nested
    @DisplayName("when the Bearer token is valid")
    class ValidToken {

        @Test
        @DisplayName("strips the 'Bearer ' prefix before handing the token to JwtService")
        void shouldStripTheBearerPrefix() {
            chainCompletes();
            when(jwtService.getClaimsFromToken(anyString()))
                    .thenReturn(new TokenClaims(TestTokens.USER_ID, TestTokens.ROLE, TestTokens.EMAIL));

            String rawToken = TestTokens.validToken();
            MockServerWebExchange exchange = exchangeFor(PROTECTED_PATH, TestTokens.bearer(rawToken));

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            verify(jwtService).getClaimsFromToken(rawToken);
        }

        @Test
        @DisplayName("injects X-user-id / X-user-role / X-user-email for the downstream service")
        void shouldInjectIdentityHeaders() {
            chainCompletes();
            when(jwtService.getClaimsFromToken(anyString()))
                    .thenReturn(new TokenClaims(TestTokens.USER_ID, "ADMIN", TestTokens.EMAIL));

            MockServerWebExchange exchange =
                    exchangeFor(PROTECTED_PATH, TestTokens.bearer(TestTokens.validToken()));

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            HttpHeaders forwarded = captureForwardedExchange().getRequest().getHeaders();
            assertThat(forwarded.getFirst("X-user-id")).isEqualTo(TestTokens.USER_ID);
            assertThat(forwarded.getFirst("X-user-role")).isEqualTo("ADMIN");
            assertThat(forwarded.getFirst("X-user-email")).isEqualTo(TestTokens.EMAIL);
            assertThat(exchange.getResponse().getStatusCode()).isNull();
        }

        @Test
        @DisplayName("overwrites identity headers supplied by the client (no header spoofing)")
        void shouldOverwriteClientSuppliedIdentityHeaders() {
            chainCompletes();
            when(jwtService.getClaimsFromToken(anyString()))
                    .thenReturn(new TokenClaims(TestTokens.USER_ID, "USER", TestTokens.EMAIL));

            MockServerHttpRequest request = MockServerHttpRequest.get(BASE_URL + PROTECTED_PATH)
                    .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.validToken()))
                    .header("X-user-id", "1")
                    .header("X-user-role", "ADMIN")
                    .header("X-user-email", "attacker@example.com")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            HttpHeaders forwarded = captureForwardedExchange().getRequest().getHeaders();
            assertThat(forwarded.get("X-user-id")).containsExactly(TestTokens.USER_ID);
            assertThat(forwarded.get("X-user-role")).containsExactly("USER");
            assertThat(forwarded.get("X-user-email")).containsExactly(TestTokens.EMAIL);
        }

        @Test
        @DisplayName("keeps the original request headers, including Authorization")
        void shouldKeepTheOriginalHeaders() {
            chainCompletes();
            when(jwtService.getClaimsFromToken(anyString()))
                    .thenReturn(new TokenClaims(TestTokens.USER_ID, TestTokens.ROLE, TestTokens.EMAIL));

            String bearer = TestTokens.bearer(TestTokens.validToken());
            MockServerHttpRequest request = MockServerHttpRequest.get(BASE_URL + PROTECTED_PATH)
                    .header(HttpHeaders.AUTHORIZATION, bearer)
                    .header("X-correlation-id", "corr-1")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            HttpHeaders forwarded = captureForwardedExchange().getRequest().getHeaders();
            assertThat(forwarded.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo(bearer);
            assertThat(forwarded.getFirst("X-correlation-id")).isEqualTo("corr-1");
        }

        @Test
        @DisplayName("forwards a mutated copy and leaves the incoming exchange untouched")
        void shouldNotMutateTheIncomingExchange() {
            chainCompletes();
            when(jwtService.getClaimsFromToken(anyString()))
                    .thenReturn(new TokenClaims(TestTokens.USER_ID, TestTokens.ROLE, TestTokens.EMAIL));

            MockServerWebExchange exchange =
                    exchangeFor(PROTECTED_PATH, TestTokens.bearer(TestTokens.validToken()));

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            ServerWebExchange forwarded = captureForwardedExchange();
            assertThat(forwarded).isNotSameAs(exchange);
            assertThat(exchange.getRequest().getHeaders().containsKey("X-user-id")).isFalse();
        }

        @Test
        @DisplayName("keeps the request method, uri and query string intact")
        void shouldKeepRequestLineIntact() {
            chainCompletes();
            when(jwtService.getClaimsFromToken(anyString()))
                    .thenReturn(new TokenClaims(TestTokens.USER_ID, TestTokens.ROLE, TestTokens.EMAIL));

            MockServerHttpRequest request = MockServerHttpRequest
                    .post(BASE_URL + "/orders?page=2&size=10")
                    .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.validToken()))
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            ServerWebExchange forwarded = captureForwardedExchange();
            assertThat(forwarded.getRequest().getMethod()).isEqualTo(request.getMethod());
            assertThat(forwarded.getRequest().getURI().getPath()).isEqualTo("/orders");
            assertThat(forwarded.getRequest().getQueryParams().getFirst("page")).isEqualTo("2");
            assertThat(forwarded.getRequest().getQueryParams().getFirst("size")).isEqualTo("10");
        }

        @Test
        @DisplayName("a token without role/email still passes, the headers simply stay empty")
        void shouldForwardEvenWhenCustomClaimsAreMissing() {
            chainCompletes();
            when(jwtService.getClaimsFromToken(anyString()))
                    .thenReturn(new TokenClaims(TestTokens.USER_ID, null, null));

            MockServerWebExchange exchange =
                    exchangeFor(PROTECTED_PATH, TestTokens.bearer(TestTokens.tokenWithoutCustomClaims()));

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            HttpHeaders forwarded = captureForwardedExchange().getRequest().getHeaders();
            assertThat(forwarded.getFirst("X-user-id")).isEqualTo(TestTokens.USER_ID);
            assertThat(forwarded.getFirst("X-user-role")).isNull();
            assertThat(forwarded.getFirst("X-user-email")).isNull();
            assertThat(exchange.getResponse().getStatusCode()).isNull();
        }

        @Test
        @DisplayName("propagates the outcome of the downstream chain")
        void shouldPropagateChainOutcome() {
            RuntimeException downstreamFailure = new IllegalStateException("downstream exploded");
            when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.error(downstreamFailure));
            when(jwtService.getClaimsFromToken(anyString()))
                    .thenReturn(new TokenClaims(TestTokens.USER_ID, TestTokens.ROLE, TestTokens.EMAIL));

            MockServerWebExchange exchange =
                    exchangeFor(PROTECTED_PATH, TestTokens.bearer(TestTokens.validToken()));

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyErrorSatisfies(error -> assertThat(error).isSameAs(downstreamFailure));
        }
    }
}







