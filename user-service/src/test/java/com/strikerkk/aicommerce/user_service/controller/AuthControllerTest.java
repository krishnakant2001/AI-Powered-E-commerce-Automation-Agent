package com.strikerkk.aicommerce.user_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.strikerkk.aicommerce.user_service.common.PageResponse;
import com.strikerkk.aicommerce.user_service.dto.request.CreateUserRequest;
import com.strikerkk.aicommerce.user_service.dto.request.LoginUserRequest;
import com.strikerkk.aicommerce.user_service.dto.request.UpdateUserDetailsRequest;
import com.strikerkk.aicommerce.user_service.dto.response.AuthResponse;
import com.strikerkk.aicommerce.user_service.dto.response.UserResponse;
import com.strikerkk.aicommerce.user_service.exception.BadRequestException;
import com.strikerkk.aicommerce.user_service.exception.ResourceNotFoundException;
import com.strikerkk.aicommerce.user_service.exception.advice.GlobalExceptionHandler;
import com.strikerkk.aicommerce.user_service.service.AuthService;
import com.strikerkk.aicommerce.user_service.service.UserService;
import com.strikerkk.aicommerce.user_service.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController")
class AuthControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ------------------------------------------------------------------- POST /auth/signup

    @Nested
    @DisplayName("POST /auth/signup")
    class Signup {

        @Test
        @DisplayName("returns 201 together with the created user")
        void shouldCreateUser() throws Exception {
            UserResponse userResponse = TestDataFactory.userResponse();
            when(userService.registerUser(any(CreateUserRequest.class))).thenReturn(userResponse);

            mockMvc.perform(post("/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(TestDataFactory.createUserRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("User created successfully"))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.email").value(TestDataFactory.EMAIL))
                    .andExpect(jsonPath("$.data.role").value("USER"))
                    .andExpect(jsonPath("$.data.password").doesNotExist());
        }

        @Test
        @DisplayName("returns 400 when the email is malformed and never calls the service")
        void shouldRejectMalformedEmail() throws Exception {
            CreateUserRequest request = TestDataFactory.createUserRequest();
            request.setEmail("not-an-email");

            mockMvc.perform(post("/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("email: Invalid email format"));

            verify(userService, never()).registerUser(any(CreateUserRequest.class));
        }

        @Test
        @DisplayName("returns 400 when the password is too short")
        void shouldRejectShortPassword() throws Exception {
            CreateUserRequest request = TestDataFactory.createUserRequest();
            request.setPassword("short");

            mockMvc.perform(post("/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("password: Password must be at least 8 characters"));
        }

        @Test
        @DisplayName("returns 400 when the first name is blank")
        void shouldRejectBlankFirstName() throws Exception {
            CreateUserRequest request = TestDataFactory.createUserRequest();
            request.setFirstName("   ");

            mockMvc.perform(post("/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("firstName: First name is required"));
        }

        @Test
        @DisplayName("returns 400 when the email is already registered")
        void shouldReturnBadRequestForDuplicateEmail() throws Exception {
            when(userService.registerUser(any(CreateUserRequest.class)))
                    .thenThrow(new BadRequestException(
                            "User with email " + TestDataFactory.EMAIL + " is already registered"));

            mockMvc.perform(post("/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(TestDataFactory.createUserRequest())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message")
                            .value("User with email " + TestDataFactory.EMAIL + " is already registered"));
        }
    }

    // -------------------------------------------------------------------- POST /auth/login

    @Nested
    @DisplayName("POST /auth/login")
    class Login {

        @Test
        @DisplayName("returns 200 together with the access token")
        void shouldLogUserIn() throws Exception {
            AuthResponse authResponse =
                    new AuthResponse("generated.jwt.token", "Bearer", TestDataFactory.userResponse());
            when(authService.loginUser(any(LoginUserRequest.class))).thenReturn(authResponse);

            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(TestDataFactory.loginUserRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Login successful"))
                    .andExpect(jsonPath("$.data.accessToken").value("generated.jwt.token"))
                    .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.data.user.email").value(TestDataFactory.EMAIL));
        }

        @Test
        @DisplayName("returns 401 with a neutral message for invalid credentials")
        void shouldReturnUnauthorizedForInvalidCredentials() throws Exception {
            when(authService.loginUser(any(LoginUserRequest.class)))
                    .thenThrow(new org.springframework.security.authentication.BadCredentialsException("Bad credentials"));

            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(TestDataFactory.loginUserRequest())))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.error").value("Invalid email or password"))
                    .andExpect(jsonPath("$.path").value("/auth/login"));
        }

        @Test
        @DisplayName("returns 400 when the password is missing")
        void shouldRejectMissingPassword() throws Exception {
            LoginUserRequest request = TestDataFactory.loginUserRequest();
            request.setPassword("");

            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("password: Password is required"));

            verify(authService, never()).loginUser(any(LoginUserRequest.class));
        }
    }

    // ------------------------------------------------------------------------ GET /details

    @Nested
    @DisplayName("GET /details")
    class Details {

        @Test
        @DisplayName("returns 200 together with the current user")
        void shouldReturnCurrentUser() throws Exception {
            when(userService.userDetails()).thenReturn(TestDataFactory.userResponse());

            mockMvc.perform(get("/details"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Successfully fetch user details"))
                    .andExpect(jsonPath("$.data.email").value(TestDataFactory.EMAIL));
        }

        @Test
        @DisplayName("returns 500 when the user behind the token no longer exists")
        void shouldReturnServerErrorWhenUserIsMissing() throws Exception {
            when(userService.userDetails()).thenThrow(new RuntimeException("User not found"));

            mockMvc.perform(get("/details"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("User not found"));
        }
    }

    // ------------------------------------------------------------ PUT /update/user/details

    @Nested
    @DisplayName("PUT /update/user/details")
    class UpdateDetails {

        @Test
        @DisplayName("returns 200 together with the updated user")
        void shouldUpdateUserDetails() throws Exception {
            when(userService.updateUserDetails(any(UpdateUserDetailsRequest.class)))
                    .thenReturn(TestDataFactory.userResponse());

            mockMvc.perform(put("/update/user/details")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(TestDataFactory.updateUserDetailsRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Successfully update user details"))
                    .andExpect(jsonPath("$.data.id").value(1));
        }

        @Test
        @DisplayName("returns 400 when the phone number is malformed")
        void shouldRejectMalformedPhoneNumber() throws Exception {
            UpdateUserDetailsRequest request = TestDataFactory.updateUserDetailsRequest();
            request.setPhoneNumber("12345");

            mockMvc.perform(put("/update/user/details")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("phoneNumber: Invalid phone number format"));

            verify(userService, never()).updateUserDetails(any(UpdateUserDetailsRequest.class));
        }
    }

    // ------------------------------------------------------ GET /admin/all/user/details

    @Nested
    @DisplayName("GET /admin/all/user/details")
    class AllUserDetails {

        @Test
        @DisplayName("returns 200 together with the paginated users")
        void shouldReturnPaginatedUsers() throws Exception {
            PageResponse<UserResponse> pageResponse = new PageResponse<>(
                    new PageImpl<UserResponse>(List.of(TestDataFactory.userResponse()), PageRequest.of(0, 20), 1));
            when(userService.allUserDetails(anyInt(), anyInt())).thenReturn(pageResponse);

            mockMvc.perform(get("/admin/all/user/details"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Successfully fetched all user details"))
                    .andExpect(jsonPath("$.data.content", org.hamcrest.Matchers.hasSize(1)))
                    .andExpect(jsonPath("$.data.page").value(0))
                    .andExpect(jsonPath("$.data.size").value(20))
                    .andExpect(jsonPath("$.data.totalElements").value(1))
                    .andExpect(jsonPath("$.data.totalPages").value(1));
        }

        @Test
        @DisplayName("falls back to page 0 and size 20 when no parameter is supplied")
        void shouldApplyDefaultPagination() throws Exception {
            when(userService.allUserDetails(0, 20)).thenReturn(new PageResponse<>(
                    new PageImpl<UserResponse>(java.util.Collections.emptyList(), PageRequest.of(0, 20), 0)));

            mockMvc.perform(get("/admin/all/user/details"))
                    .andExpect(status().isOk());

            verify(userService).allUserDetails(0, 20);
        }

        @Test
        @DisplayName("forwards the supplied page and size parameters")
        void shouldForwardPaginationParameters() throws Exception {
            when(userService.allUserDetails(2, 5)).thenReturn(new PageResponse<>(
                    new PageImpl<UserResponse>(java.util.Collections.emptyList(), PageRequest.of(2, 5), 0)));

            mockMvc.perform(get("/admin/all/user/details")
                            .param("page", "2")
                            .param("size", "5"))
                    .andExpect(status().isOk());

            verify(userService).allUserDetails(2, 5);
        }
    }

    // --------------------------------------------------- DELETE /admin/delete/user/{userId}

    @Nested
    @DisplayName("DELETE /admin/delete/user/{userId}")
    class DeleteUser {

        @Test
        @DisplayName("returns 200 and an envelope without any payload")
        void shouldDeleteUser() throws Exception {
            doNothing().when(userService).deleteUser("7");

            mockMvc.perform(delete("/admin/delete/user/{userId}", "7"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Successfully delete the user with id 7"))
                    .andExpect(jsonPath("$.data").doesNotExist());

            verify(userService).deleteUser("7");
        }

        @Test
        @DisplayName("returns 404 when the user does not exist")
        void shouldReturnNotFoundWhenUserIsMissing() throws Exception {
            doThrow(new ResourceNotFoundException("User not found with userId 7"))
                    .when(userService).deleteUser(anyString());

            mockMvc.perform(delete("/admin/delete/user/{userId}", "7"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("User not found with userId 7"));
        }
    }
}


