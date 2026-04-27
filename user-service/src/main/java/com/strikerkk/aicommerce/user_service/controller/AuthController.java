package com.strikerkk.aicommerce.user_service.controller;

import com.strikerkk.aicommerce.user_service.common.ApiResponse;
import com.strikerkk.aicommerce.user_service.dto.request.CreateUserRequest;
import com.strikerkk.aicommerce.user_service.dto.request.LoginUserRequest;
import com.strikerkk.aicommerce.user_service.dto.request.UpdateUserDetailsRequest;
import com.strikerkk.aicommerce.user_service.dto.response.AuthResponse;
import com.strikerkk.aicommerce.user_service.dto.response.UserResponse;
import com.strikerkk.aicommerce.user_service.service.AuthService;
import com.strikerkk.aicommerce.user_service.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final AuthService authService;

    @PostMapping("/auth/signup")
    public ResponseEntity<ApiResponse<UserResponse>> userRegistration(@Valid @RequestBody CreateUserRequest request) {

        UserResponse userResponse = userService.registerUser(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("User created successfully", userResponse));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<ApiResponse<AuthResponse>> userLogin(@Valid @RequestBody LoginUserRequest request) {

        AuthResponse authResponse = authService.loginUser(request);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Login successful", authResponse));
    }

    @GetMapping("/details")
    public ResponseEntity<ApiResponse<UserResponse>> userDetails() {

        UserResponse userResponse = userService.userDetails();

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Successfully fetch user details", userResponse));
    }

    @PutMapping("/update/user/details")
    public ResponseEntity<ApiResponse<UserResponse>> updateUserDetails(@RequestBody UpdateUserDetailsRequest request) {

        UserResponse userResponse = userService.updateUserDetails(request);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Successfully update user details", userResponse));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("admin/all/user/details")
    public ResponseEntity<ApiResponse<List<UserResponse>>> allUserDetails() {

        List<UserResponse> userResponseList = userService.allUserDetails();

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Successfully fetch all user details",userResponseList));

    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("admin/delete/user/{userId}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable String userId) {

        userService.deleteUser(userId);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Successfully delete the user with id " + userId));
    }
}
