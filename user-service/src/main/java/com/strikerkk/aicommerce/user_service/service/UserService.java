package com.strikerkk.aicommerce.user_service.service;

import com.strikerkk.aicommerce.user_service.auth.UserContext;
import com.strikerkk.aicommerce.user_service.dto.request.CreateUserRequest;
import com.strikerkk.aicommerce.user_service.dto.request.UpdateUserDetailsRequest;
import com.strikerkk.aicommerce.user_service.dto.response.UserResponse;
import com.strikerkk.aicommerce.user_service.exception.BadRequestException;
import com.strikerkk.aicommerce.user_service.exception.ResourceNotFoundException;
import com.strikerkk.aicommerce.user_service.security.model.CustomUserDetails;
import com.strikerkk.aicommerce.user_service.entity.User;
import com.strikerkk.aicommerce.user_service.repository.UserRepository;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


@Slf4j
@Service
@Builder
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final ModelMapper modelMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository
                .findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User with email " + email + " not found"));

        return new CustomUserDetails(user);
    }


    public UserResponse registerUser(CreateUserRequest request) {

        // Check email is already present or not
        if(userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("User with email " + request.getEmail() + " is already registered");
        }

        log.info("Registration of new user with email={}", request.getEmail());

        // Create new user
        User newUser = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .addresses(new ArrayList<>())
                .phoneNumber(request.getPhoneNumber())
                .build();

        User savedUser = userRepository.save(newUser);

        return modelMapper.map(savedUser, UserResponse.class);
    }


    public UserResponse userDetails() {

        Long userId = Long.valueOf(UserContext.getUserId());

        log.info("Getting user details for userId={}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return modelMapper.map(user, UserResponse.class);
    }

    public UserResponse updateUserDetails(UpdateUserDetailsRequest request) {

        Long userId = Long.valueOf(UserContext.getUserId());

        log.info("Updating user details for userId={}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setUpdatedAt(LocalDateTime.now());

        return modelMapper.map(user, UserResponse.class);
    }


    public List<UserResponse> allUserDetails() {

        log.info("Getting list of all registered users");

        List<User> userList = userRepository.findAll();
        return userList
                .stream()
                .map(user -> modelMapper.map(user, UserResponse.class))
                .toList();
    }

    public void deleteUser(String userId) {

        if(!userRepository.existsById(Long.valueOf(userId))) {
            throw new ResourceNotFoundException("User not found with userId " + userId);
        }

        log.info("Deleting user of id={}", userId);

        userRepository.deleteById(Long.valueOf(userId));
    }
}
