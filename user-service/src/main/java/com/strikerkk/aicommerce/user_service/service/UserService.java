package com.strikerkk.aicommerce.user_service.service;

import com.strikerkk.aicommerce.user_service.auth.UserContext;
import com.strikerkk.aicommerce.user_service.common.PageResponse;
import com.strikerkk.aicommerce.user_service.dto.request.CreateUserRequest;
import com.strikerkk.aicommerce.user_service.dto.request.UpdateUserDetailsRequest;
import com.strikerkk.aicommerce.user_service.dto.response.UserResponse;
import com.strikerkk.aicommerce.user_service.exception.BadRequestException;
import com.strikerkk.aicommerce.user_service.exception.ResourceNotFoundException;
import com.strikerkk.aicommerce.user_service.security.model.CustomUserDetails;
import com.strikerkk.aicommerce.user_service.entity.User;
import com.strikerkk.aicommerce.user_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final ModelMapper modelMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

        // Check email before db call
        String normalizedEmail = email != null ? email.trim().toLowerCase(Locale.ROOT) : null;
        if(normalizedEmail == null || normalizedEmail.isBlank()) {
            throw new UsernameNotFoundException("Email is required");
        }

        User user = userRepository
                .findByEmail(normalizedEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User with email " + normalizedEmail + " not found"));

        return new CustomUserDetails(user);
    }


    @Transactional
    public UserResponse registerUser(CreateUserRequest request) {

        String normalizedEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);

        log.info("Registration of new user with email={}", normalizedEmail);

        // Create new user
        User newUser = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(normalizedEmail)
                .password(passwordEncoder.encode(request.getPassword()))
                .addresses(new ArrayList<>())
                .phoneNumber(request.getPhoneNumber())
                .build();

        User savedUser;
        try {
            savedUser = userRepository.save(newUser);
        } catch (DataIntegrityViolationException ex) {
            // Check if the user is already registered with the same email
            throw new BadRequestException("User with email " + normalizedEmail + " is already registered");
        }

        return modelMapper.map(savedUser, UserResponse.class);
    }


    public UserResponse userDetails() {

        Long userId = Long.valueOf(UserContext.getUserId());

        log.info("Getting user details for userId={}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return modelMapper.map(user, UserResponse.class);
    }

    @Transactional
    public UserResponse updateUserDetails(UpdateUserDetailsRequest request) {

        Long userId = Long.valueOf(UserContext.getUserId());

        log.info("Updating user details for userId={}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setUpdatedAt(LocalDateTime.now());

        User savedUser = userRepository.save(user);

        return modelMapper.map(savedUser, UserResponse.class);
    }


    public PageResponse<UserResponse> allUserDetails(int page, int size) {

        // Fetching users with pagination
        log.info("Fetching users - page: {}, size: {}", page, size);

        Page<User> userPage = userRepository.findAll(PageRequest.of(page, size));

        Page<UserResponse> mappedPage = userPage
                .map(user -> modelMapper.map(user, UserResponse.class));

        return new PageResponse<>(mappedPage);

    }

    public void deleteUser(String userId) {

        log.info("Deleting user of id={}", userId);

        try {
            userRepository.deleteById(Long.valueOf(userId));
        } catch (EmptyResultDataAccessException ex) {
            throw new ResourceNotFoundException("User not found with userId " + userId);
        }
    }
}
