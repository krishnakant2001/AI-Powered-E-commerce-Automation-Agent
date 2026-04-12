package com.strikerkk.aicommerce.user_service.controller;

import com.strikerkk.aicommerce.user_service.common.ApiResponse;
import com.strikerkk.aicommerce.user_service.dto.request.AddressRequest;
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

import java.util.List;

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

    @GetMapping("/details")
    public ResponseEntity<ApiResponse<UserResponse>> userDetails(@RequestHeader("X-user-id") String userId) {

        UserResponse userResponse = userService.userDetails(userId);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Successfully fetch user details", userResponse));
    }

    @PostMapping("/address/add")
    public ResponseEntity<ApiResponse<AddressResponse>> addAddress(
            @Valid @RequestBody AddressRequest request, @RequestHeader("X-user-id") String userId) {

        AddressResponse addressResponse = addressService.addAddress(request, userId);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Address added successfully", addressResponse));
    }

    @GetMapping("address/all")
    public ResponseEntity<ApiResponse<List<AddressResponse>>> allAddresses(@RequestHeader("X-user-id") String userId) {

        List<AddressResponse> addressResponseList = addressService.allAddresses(userId);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Successfully fetch all addresses", addressResponseList));

    }

    @PutMapping("address/update/{addressId}")
    public ResponseEntity<ApiResponse<AddressResponse>> updateAddress(
            @Valid @RequestBody AddressRequest request, @PathVariable Long addressId,
            @RequestHeader("X-user-id") String userId) {

        AddressResponse addressResponse = addressService.updateAddress(request, addressId, userId);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Address updated successfully", addressResponse));
    }

    @DeleteMapping("address/delete/{addressId}")
    public ResponseEntity<ApiResponse<Void>> deleteAddress(
            @PathVariable Long addressId, @RequestHeader("X-user-id") String userId) {

        addressService.deleteAddress(addressId, userId);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Account deleted successfully"));
    }
}
