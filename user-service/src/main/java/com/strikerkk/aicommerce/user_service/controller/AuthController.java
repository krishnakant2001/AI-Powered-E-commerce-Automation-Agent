package com.strikerkk.aicommerce.user_service.controller;

import com.strikerkk.aicommerce.user_service.common.ApiResponse;
import com.strikerkk.aicommerce.user_service.dto.request.AddNewAddressRequest;
import com.strikerkk.aicommerce.user_service.dto.request.CreateUserRequest;
import com.strikerkk.aicommerce.user_service.dto.request.LoginUserRequest;
import com.strikerkk.aicommerce.user_service.dto.response.AddressResponse;
import com.strikerkk.aicommerce.user_service.dto.response.AuthResponse;
import com.strikerkk.aicommerce.user_service.dto.response.UserResponse;
import com.strikerkk.aicommerce.user_service.service.AddressService;
import com.strikerkk.aicommerce.user_service.service.AuthService;
import com.strikerkk.aicommerce.user_service.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final AuthService authService;
    private final AddressService addressService;

    @PostMapping("/auth/signup")
    public ResponseEntity<ApiResponse<UserResponse>> userRegistration(
            @Valid @RequestBody CreateUserRequest request) {

        UserResponse userResponse = userService.registerUser(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("User created successfully", userResponse));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<ApiResponse<AuthResponse>> userLogin(
            @Valid @RequestBody LoginUserRequest request) {

        AuthResponse authResponse = authService.loginUser(request);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Login successful", authResponse));
    }

    @PostMapping("/address/add")
    public ResponseEntity<ApiResponse<AddressResponse>> addAddress(
            @Valid @RequestBody AddNewAddressRequest request, @RequestHeader("X-user-id") String userId) {

        AddressResponse addressResponse = addressService.addAddress(request, userId);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Added address successfully", addressResponse));
    }

}
