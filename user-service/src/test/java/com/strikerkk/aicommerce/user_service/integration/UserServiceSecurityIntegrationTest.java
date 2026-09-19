package com.strikerkk.aicommerce.user_service.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.strikerkk.aicommerce.user_service.common.PageResponse;
import com.strikerkk.aicommerce.user_service.dto.request.AddressRequest;
import com.strikerkk.aicommerce.user_service.dto.request.CreateUserRequest;
import com.strikerkk.aicommerce.user_service.dto.response.AddressResponse;
import com.strikerkk.aicommerce.user_service.dto.response.UserResponse;
import com.strikerkk.aicommerce.user_service.service.AddressService;
import com.strikerkk.aicommerce.user_service.service.AuthService;
import com.strikerkk.aicommerce.user_service.service.UserService;
import com.strikerkk.aicommerce.user_service.support.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("User service HTTP security wiring")
class UserServiceSecurityIntegrationTest {

    private static final String USER_ID_HEADER = "X-user-id";
    private static final String USER_ROLE_HEADER = "X-user-role";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AddressService addressService;

    private PageResponse<UserResponse> emptyPage() {
        return new PageResponse<>(
                new PageImpl<UserResponse>(Collections.emptyList(), PageRequest.of(0, 20), 0));
    }

    @Test
    @DisplayName("the sign up endpoint is public")
    void signupIsPublic() throws Exception {
        when(userService.registerUser(any(CreateUserRequest.class)))
                .thenReturn(TestDataFactory.userResponse());

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TestDataFactory.createUserRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value(TestDataFactory.EMAIL));
    }

    @Test
    @DisplayName("an ADMIN forwarded by the gateway can reach the administrative endpoints")
    void adminCanReachAdminEndpoints() throws Exception {
        when(userService.allUserDetails(anyInt(), anyInt())).thenReturn(emptyPage());

        mockMvc.perform(get("/admin/all/user/details")
                        .header(USER_ID_HEADER, "1")
                        .header(USER_ROLE_HEADER, "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("a plain USER receives 403 from the administrative endpoints")
    void userIsForbiddenOnAdminEndpoints() throws Exception {
        mockMvc.perform(get("/admin/all/user/details")
                        .header(USER_ID_HEADER, "1")
                        .header(USER_ROLE_HEADER, "USER"))
                .andExpect(status().isForbidden());

        verify(userService, never()).allUserDetails(anyInt(), anyInt());
    }

    @Test
    @DisplayName("an anonymous caller receives 401 from the administrative endpoints")
    void anonymousIsUnauthorizedOnAdminEndpoints() throws Exception {
        mockMvc.perform(get("/admin/all/user/details"))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).allUserDetails(anyInt(), anyInt());
    }

    @Test
    @DisplayName("an ADMIN forwarded by the gateway can delete a user")
    void adminCanDeleteUser() throws Exception {
        mockMvc.perform(delete("/admin/delete/user/{userId}", "7")
                        .header(USER_ID_HEADER, "1")
                        .header(USER_ROLE_HEADER, "ADMIN"))
                .andExpect(status().isOk());

        verify(userService).deleteUser("7");
    }

    @Test
    @DisplayName("a plain USER cannot delete another user")
    void userCannotDeleteUser() throws Exception {
        mockMvc.perform(delete("/admin/delete/user/{userId}", "7")
                        .header(USER_ID_HEADER, "1")
                        .header(USER_ROLE_HEADER, "USER"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("the address endpoints are reachable with the gateway headers")
    void addressEndpointsAreReachable() throws Exception {
        AddressResponse addressResponse = TestDataFactory.addressResponse(10L, true);
        when(addressService.allAddresses()).thenReturn(List.of(addressResponse));
        when(addressService.addAddress(any(AddressRequest.class))).thenReturn(addressResponse);

        mockMvc.perform(get("/address/all")
                        .header(USER_ID_HEADER, "1")
                        .header(USER_ROLE_HEADER, "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(10));

        mockMvc.perform(post("/address/add")
                        .header(USER_ID_HEADER, "1")
                        .header(USER_ROLE_HEADER, "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TestDataFactory.addressRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(10));
    }

    @Test
    @DisplayName("bean validation is active on the real web stack")
    void beanValidationIsActive() throws Exception {
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
}

