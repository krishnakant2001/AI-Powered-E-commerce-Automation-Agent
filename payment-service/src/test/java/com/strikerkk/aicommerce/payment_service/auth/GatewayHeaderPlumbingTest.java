package com.strikerkk.aicommerce.payment_service.auth;

import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("The gateway header plumbing")
class GatewayHeaderPlumbingTest {

    private static final String HEADER = "X-user-id";

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    // ==================================================================
    // inbound
    // ==================================================================

    @Nested
    @DisplayName("UserInterceptor (inbound)")
    class Inbound {

        private final UserInterceptor interceptor = new UserInterceptor();

        private MockHttpServletRequest requestWith(String userId) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            if (userId != null) {
                request.addHeader(HEADER, userId);
            }
            return request;
        }

        @Test
        @DisplayName("moves the gateway header into the thread")
        void movesTheHeaderIntoTheThread() throws Exception {
            interceptor.preHandle(requestWith("42"), new MockHttpServletResponse(), new Object());

            assertThat(UserContext.getUserId()).isEqualTo("42");
        }

        @Test
        @DisplayName("always lets the request through, authentication happened at the gateway")
        void alwaysLetsTheRequestThrough() throws Exception {
            assertThat(interceptor.preHandle(requestWith("42"), new MockHttpServletResponse(), new Object()))
                    .isTrue();
            assertThat(interceptor.preHandle(requestWith(null), new MockHttpServletResponse(), new Object()))
                    .isTrue();
        }

        @Test
        @DisplayName("leaves the thread empty when the header is missing - the service then refuses")
        void leavesTheThreadEmptyWithoutTheHeader() throws Exception {
            interceptor.preHandle(requestWith(null), new MockHttpServletResponse(), new Object());

            assertThat(UserContext.getUserId()).isNull();
        }

        @Test
        @DisplayName("never overwrites the thread with a null header")
        void neverOverwritesWithANullHeader() throws Exception {
            UserContext.setUserId("42");

            interceptor.preHandle(requestWith(null), new MockHttpServletResponse(), new Object());

            assertThat(UserContext.getUserId()).isEqualTo("42");
        }

        @Test
        @DisplayName("reads the header case insensitively, as HTTP demands")
        void readsTheHeaderCaseInsensitively() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-USER-ID", "42");

            interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

            assertThat(UserContext.getUserId()).isEqualTo("42");
        }

        @Test
        @DisplayName("copies the header verbatim, it does not parse it")
        void copiesTheHeaderVerbatim() throws Exception {
            interceptor.preHandle(requestWith("not-a-number"), new MockHttpServletResponse(), new Object());

            assertThat(UserContext.getUserId()).isEqualTo("not-a-number");
        }

        @Test
        @DisplayName("empties the thread once the response is written, the thread goes back to the pool")
        void emptiesTheThreadAfterwards() throws Exception {
            UserContext.setUserId("42");

            interceptor.afterCompletion(
                    requestWith("42"), new MockHttpServletResponse(), new Object(), null);

            assertThat(UserContext.getUserId()).isNull();
        }

        @Test
        @DisplayName("empties the thread even when the request blew up")
        void emptiesTheThreadAfterAFailure() throws Exception {
            UserContext.setUserId("42");

            interceptor.afterCompletion(requestWith("42"), new MockHttpServletResponse(), new Object(),
                    new RuntimeException("Payment already exists for this order"));

            assertThat(UserContext.getUserId()).isNull();
        }

        @Test
        @DisplayName("is a Spring component implementing HandlerInterceptor")
        void isASpringComponent() {
            assertThat(UserInterceptor.class.getAnnotation(Component.class)).isNotNull();
            assertThat(HandlerInterceptor.class).isAssignableFrom(UserInterceptor.class);
        }
    }

    // ==================================================================
    // outbound
    // ==================================================================

    @Nested
    @DisplayName("FeignClientInterceptor (outbound)")
    class Outbound {

        private final FeignClientInterceptor interceptor = new FeignClientInterceptor();

        @Test
        @DisplayName("puts the caller back on the wire, so order-service knows who is paying")
        void putsTheCallerBackOnTheWire() {
            UserContext.setUserId("42");
            RequestTemplate template = new RequestTemplate();

            interceptor.apply(template);

            assertThat(template.headers()).containsKey(HEADER);
            assertThat(template.headers().get(HEADER)).containsExactly("42");
        }

        @Test
        @DisplayName("sends no header at all when there is no caller")
        void sendsNoHeaderWithoutACaller() {
            RequestTemplate template = new RequestTemplate();

            interceptor.apply(template);

            assertThat(template.headers()).doesNotContainKey(HEADER);
        }

        @Test
        @DisplayName("forwards whatever the thread holds, it does not validate it")
        void forwardsWhateverTheThreadHolds() {
            UserContext.setUserId("not-a-number");
            RequestTemplate template = new RequestTemplate();

            interceptor.apply(template);

            assertThat(template.headers().get(HEADER)).containsExactly("not-a-number");
        }

        @Test
        @DisplayName("adds the header to every outgoing call, never removes anything")
        void keepsTheOtherHeaders() {
            UserContext.setUserId("42");
            RequestTemplate template = new RequestTemplate();
            template.header("Accept", "application/json");

            interceptor.apply(template);

            assertThat(template.headers()).containsKeys("Accept", HEADER);
        }

        @Test
        @DisplayName("is a Spring component implementing the Feign RequestInterceptor")
        void isASpringComponent() {
            assertThat(FeignClientInterceptor.class.getAnnotation(Component.class)).isNotNull();
            assertThat(feign.RequestInterceptor.class).isAssignableFrom(FeignClientInterceptor.class);
        }
    }

    // ==================================================================
    // registration
    // ==================================================================

    @Nested
    @DisplayName("WebConfig")
    class Registration {

        @Test
        @DisplayName("plugs the UserInterceptor into the MVC pipeline")
        void plugsTheInterceptorIn() {
            WebConfig webConfig = new WebConfig();
            UserInterceptor userInterceptor = new UserInterceptor();
            ReflectionTestUtils.setField(webConfig, "userInterceptor", userInterceptor);

            InterceptorRegistry registry = new InterceptorRegistry();
            webConfig.addInterceptors(registry);

            @SuppressWarnings("unchecked")
            List<Object> registrations =
                    (List<Object>) ReflectionTestUtils.invokeMethod(registry, "getInterceptors");

            assertThat(registrations).contains(userInterceptor);
        }

        @Test
        @DisplayName("registers the interceptor for every path, payments and webhooks alike")
        void registersItForEveryPath() {
            WebConfig webConfig = new WebConfig();
            ReflectionTestUtils.setField(webConfig, "userInterceptor", new UserInterceptor());

            InterceptorRegistry registry = new InterceptorRegistry();
            webConfig.addInterceptors(registry);

            @SuppressWarnings("unchecked")
            List<Object> registrations =
                    (List<Object>) ReflectionTestUtils.invokeMethod(registry, "getInterceptors");

            // No addPathPatterns() call means "all paths".
            assertThat(registrations).hasSize(1);
        }

        @Test
        @DisplayName("is a configuration that customises Spring MVC")
        void isAnMvcConfiguration() {
            assertThat(WebConfig.class.getAnnotation(org.springframework.context.annotation.Configuration.class))
                    .isNotNull();
            assertThat(WebMvcConfigurer.class).isAssignableFrom(WebConfig.class);
        }
    }

    // ==================================================================
    // the round trip
    // ==================================================================

    @Test
    @DisplayName("the caller survives the whole round trip: header in, thread, header out")
    void theCallerSurvivesTheRoundTrip() throws Exception {
        HttpServletRequest request = new MockHttpServletRequest();
        ((MockHttpServletRequest) request).addHeader(HEADER, "42");

        new UserInterceptor().preHandle(request, new MockHttpServletResponse(), new Object());

        RequestTemplate template = new RequestTemplate();
        new FeignClientInterceptor().apply(template);

        assertThat(template.headers().get(HEADER)).containsExactly("42");
    }
}

