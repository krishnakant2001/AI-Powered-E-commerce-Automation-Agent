package com.strikerkk.aicommerce.user_service.service;

import com.strikerkk.aicommerce.user_service.dto.request.LoginUserRequest;
import com.strikerkk.aicommerce.user_service.dto.response.AuthResponse;
import com.strikerkk.aicommerce.user_service.dto.response.UserResponse;
import com.strikerkk.aicommerce.user_service.security.model.CustomUserDetails;
import com.strikerkk.aicommerce.user_service.security.service.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final ModelMapper modelMapper;

    public AuthResponse loginUser(LoginUserRequest request) {

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        String authToken = jwtService.generateAccessToken(userDetails.getUser());

        UserResponse userResponse = modelMapper.map(userDetails.getUser(), UserResponse.class);
        return new AuthResponse(authToken, "Bearer", userResponse);
    }
}
