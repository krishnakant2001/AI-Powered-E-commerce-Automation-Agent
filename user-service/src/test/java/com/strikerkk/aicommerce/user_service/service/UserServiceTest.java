package com.strikerkk.aicommerce.user_service.service;

import com.strikerkk.aicommerce.user_service.auth.UserContext;
import com.strikerkk.aicommerce.user_service.common.PageResponse;
import com.strikerkk.aicommerce.user_service.dto.request.CreateUserRequest;
import com.strikerkk.aicommerce.user_service.dto.request.UpdateUserDetailsRequest;
import com.strikerkk.aicommerce.user_service.dto.response.UserResponse;
import com.strikerkk.aicommerce.user_service.entity.User;
import com.strikerkk.aicommerce.user_service.entity.enums.Role;
import com.strikerkk.aicommerce.user_service.exception.BadRequestException;
import com.strikerkk.aicommerce.user_service.exception.ResourceNotFoundException;
import com.strikerkk.aicommerce.user_service.repository.UserRepository;
import com.strikerkk.aicommerce.user_service.security.model.CustomUserDetails;
import com.strikerkk.aicommerce.user_service.support.TestDataFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ModelMapper modelMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    private User user;
    private UserResponse userResponse;

    @BeforeEach
    void setUp() {
        user = TestDataFactory.user();
        userResponse = TestDataFactory.userResponse();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    // loadUserByUsername ------------------------------------------------------------------

    @Nested
    @DisplayName("loadUserByUsername()")
    class LoadUserByUsername {

        @Test
        @DisplayName("returns CustomUserDetails when the user exists")
        void shouldReturnUserDetailsWhenUserExists() {
            when(userRepository.findByEmail(TestDataFactory.EMAIL)).thenReturn(Optional.of(user));

            UserDetails userDetails = userService.loadUserByUsername(TestDataFactory.EMAIL);

            assertThat(userDetails).isInstanceOf(CustomUserDetails.class);
            assertThat(userDetails.getUsername()).isEqualTo(TestDataFactory.EMAIL);
            assertThat(userDetails.getPassword()).isEqualTo(TestDataFactory.ENCODED_PASSWORD);
            assertThat(((CustomUserDetails) userDetails).getUser()).isSameAs(user);
        }

        @Test
        @DisplayName("normalizes the email (trim + lower case) before hitting the repository")
        void shouldNormalizeEmailBeforeLookup() {
            when(userRepository.findByEmail(TestDataFactory.EMAIL)).thenReturn(Optional.of(user));

            UserDetails userDetails = userService.loadUserByUsername("   KRISHNA@Example.COM   ");

            assertThat(userDetails).isNotNull();
            verify(userRepository).findByEmail(TestDataFactory.EMAIL);
        }

        @Test
        @DisplayName("throws UsernameNotFoundException and skips the DB call when email is null")
        void shouldThrowWhenEmailIsNull() {
            assertThatThrownBy(() -> userService.loadUserByUsername(null))
                    .isInstanceOf(UsernameNotFoundException.class)
                    .hasMessage("Email is required");

            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("throws UsernameNotFoundException and skips the DB call when email is blank")
        void shouldThrowWhenEmailIsBlank() {
            assertThatThrownBy(() -> userService.loadUserByUsername("     "))
                    .isInstanceOf(UsernameNotFoundException.class)
                    .hasMessage("Email is required");

            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("throws UsernameNotFoundException when no user matches the email")
        void shouldThrowWhenUserDoesNotExist() {
            when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.loadUserByUsername("missing@example.com"))
                    .isInstanceOf(UsernameNotFoundException.class)
                    .hasMessage("User with email missing@example.com not found");
        }
    }

    // registerUser -------------------------------------------------------------------------

    @Nested
    @DisplayName("registerUser()")
    class RegisterUser {

        @Test
        @DisplayName("persists a new user with a normalized email, an encoded password and the USER role")
        void shouldRegisterUserSuccessfully() {
            CreateUserRequest request = TestDataFactory.createUserRequest();
            request.setEmail("  KRISHNA@Example.COM ");

            when(passwordEncoder.encode(TestDataFactory.RAW_PASSWORD))
                    .thenReturn(TestDataFactory.ENCODED_PASSWORD);
            when(userRepository.save(any(User.class))).thenReturn(user);
            when(modelMapper.map(user, UserResponse.class)).thenReturn(userResponse);

            UserResponse result = userService.registerUser(request);

            assertThat(result).isSameAs(userResponse);

            verify(userRepository).save(userCaptor.capture());
            User saved = userCaptor.getValue();
            assertThat(saved.getEmail()).isEqualTo(TestDataFactory.EMAIL);
            assertThat(saved.getPassword()).isEqualTo(TestDataFactory.ENCODED_PASSWORD);
            assertThat(saved.getFirstName()).isEqualTo("Krishnakant");
            assertThat(saved.getLastName()).isEqualTo("Nagvanshi");
            assertThat(saved.getPhoneNumber()).isEqualTo(TestDataFactory.PHONE_NUMBER);
            assertThat(saved.getRole()).isEqualTo(Role.USER);
            assertThat(saved.getAddresses()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("never stores the raw password")
        void shouldNeverStoreRawPassword() {
            CreateUserRequest request = TestDataFactory.createUserRequest();

            when(passwordEncoder.encode(TestDataFactory.RAW_PASSWORD))
                    .thenReturn(TestDataFactory.ENCODED_PASSWORD);
            when(userRepository.save(any(User.class))).thenReturn(user);
            when(modelMapper.map(user, UserResponse.class)).thenReturn(userResponse);

            userService.registerUser(request);

            verify(passwordEncoder).encode(TestDataFactory.RAW_PASSWORD);
            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getPassword()).isNotEqualTo(TestDataFactory.RAW_PASSWORD);
        }

        @Test
        @DisplayName("translates a DataIntegrityViolationException into a BadRequestException")
        void shouldThrowBadRequestWhenEmailAlreadyRegistered() {
            CreateUserRequest request = TestDataFactory.createUserRequest();

            when(passwordEncoder.encode(anyString())).thenReturn(TestDataFactory.ENCODED_PASSWORD);
            when(userRepository.save(any(User.class)))
                    .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

            assertThatThrownBy(() -> userService.registerUser(request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("User with email " + TestDataFactory.EMAIL + " is already registered");

            verifyNoInteractions(modelMapper);
        }
    }

    // userDetails --------------------------------------------------------------------------

    @Nested
    @DisplayName("userDetails()")
    class UserDetailsLookup {

        @Test
        @DisplayName("returns the details of the user held in the UserContext")
        void shouldReturnCurrentUserDetails() {
            UserContext.setUserId(TestDataFactory.USER_ID_AS_STRING);
            when(userRepository.findById(TestDataFactory.USER_ID)).thenReturn(Optional.of(user));
            when(modelMapper.map(user, UserResponse.class)).thenReturn(userResponse);

            UserResponse result = userService.userDetails();

            assertThat(result).isSameAs(userResponse);
            verify(userRepository).findById(TestDataFactory.USER_ID);
        }

        @Test
        @DisplayName("throws RuntimeException when the user no longer exists")
        void shouldThrowWhenUserIsMissing() {
            UserContext.setUserId("99");
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.userDetails())
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("User not found");
        }

        @Test
        @DisplayName("throws NumberFormatException when the UserContext is empty")
        void shouldThrowWhenUserContextIsEmpty() {
            assertThatThrownBy(() -> userService.userDetails())
                    .isInstanceOf(NumberFormatException.class);

            verifyNoInteractions(userRepository);
        }
    }

    // updateUserDetails --------------------------------------------------------------------

    @Nested
    @DisplayName("updateUserDetails()")
    class UpdateUserDetails {

        @Test
        @DisplayName("updates the mutable fields and refreshes updatedAt")
        void shouldUpdateUserDetails() {
            UserContext.setUserId(TestDataFactory.USER_ID_AS_STRING);
            UpdateUserDetailsRequest request = TestDataFactory.updateUserDetailsRequest();
            LocalDateTime before = LocalDateTime.now().minusSeconds(1);

            when(userRepository.findById(TestDataFactory.USER_ID)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            when(modelMapper.map(any(User.class), eq(UserResponse.class))).thenReturn(userResponse);

            UserResponse result = userService.updateUserDetails(request);

            assertThat(result).isSameAs(userResponse);

            verify(userRepository).save(userCaptor.capture());
            User saved = userCaptor.getValue();
            assertThat(saved.getFirstName()).isEqualTo("Updated");
            assertThat(saved.getLastName()).isEqualTo("Name");
            assertThat(saved.getPhoneNumber()).isEqualTo("9123456780");
            assertThat(saved.getUpdatedAt()).isAfter(before);
        }

        @Test
        @DisplayName("does not change the email or the password")
        void shouldNotChangeEmailOrPassword() {
            UserContext.setUserId(TestDataFactory.USER_ID_AS_STRING);

            when(userRepository.findById(TestDataFactory.USER_ID)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            when(modelMapper.map(any(User.class), eq(UserResponse.class))).thenReturn(userResponse);

            userService.updateUserDetails(TestDataFactory.updateUserDetailsRequest());

            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getEmail()).isEqualTo(TestDataFactory.EMAIL);
            assertThat(userCaptor.getValue().getPassword()).isEqualTo(TestDataFactory.ENCODED_PASSWORD);
        }

        @Test
        @DisplayName("throws RuntimeException when the user does not exist")
        void shouldThrowWhenUserIsMissing() {
            UserContext.setUserId("42");
            when(userRepository.findById(42L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.updateUserDetails(TestDataFactory.updateUserDetailsRequest()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("User not found");

            verify(userRepository, never()).save(any(User.class));
        }
    }

    // allUserDetails ---------------------------------------------------------------------

    @Nested
    @DisplayName("allUserDetails()")
    class AllUserDetails {

        @Test
        @DisplayName("maps the requested page into a PageResponse")
        void shouldReturnPagedUsers() {
            Page<User> page = new PageImpl<>(List.of(user), PageRequest.of(0, 20), 1);

            when(userRepository.findAll(any(Pageable.class))).thenReturn(page);
            when(modelMapper.map(any(User.class), eq(UserResponse.class))).thenReturn(userResponse);

            PageResponse<UserResponse> result = userService.allUserDetails(0, 20);

            assertThat(result.getContent()).containsExactly(userResponse);
            assertThat(result.getPage()).isZero();
            assertThat(result.getSize()).isEqualTo(20);
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getTotalPages()).isEqualTo(1);
        }

        @Test
        @DisplayName("forwards the page and size arguments to the repository")
        void shouldForwardPageable() {
            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            when(userRepository.findAll(any(Pageable.class)))
                    .thenReturn(new PageImpl<User>(Collections.emptyList(), PageRequest.of(2, 5), 0));

            userService.allUserDetails(2, 5);

            verify(userRepository).findAll(pageableCaptor.capture());
            assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(2);
            assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(5);
        }

        @Test
        @DisplayName("returns an empty PageResponse when there are no users")
        void shouldReturnEmptyPage() {
            when(userRepository.findAll(any(Pageable.class)))
                    .thenReturn(new PageImpl<User>(Collections.emptyList(), PageRequest.of(0, 20), 0));

            PageResponse<UserResponse> result = userService.allUserDetails(0, 20);

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
            assertThat(result.getTotalPages()).isZero();
            verifyNoInteractions(modelMapper);
        }
    }

    // deleteUser -------------------------------------------------------------------------

    @Nested
    @DisplayName("deleteUser()")
    class DeleteUser {

        @Test
        @DisplayName("deletes the user by its numeric id")
        void shouldDeleteUser() {
            userService.deleteUser("7");

            verify(userRepository).deleteById(7L);
        }

        @Test
        @DisplayName("translates an EmptyResultDataAccessException into a ResourceNotFoundException")
        void shouldThrowWhenUserDoesNotExist() {
            doThrow(new EmptyResultDataAccessException(1)).when(userRepository).deleteById(anyLong());

            assertThatThrownBy(() -> userService.deleteUser("7"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("User not found with userId 7");
        }

        @Test
        @DisplayName("throws NumberFormatException for a non numeric id")
        void shouldThrowForNonNumericId() {
            assertThatThrownBy(() -> userService.deleteUser("not-a-number"))
                    .isInstanceOf(NumberFormatException.class);

            verifyNoInteractions(userRepository);
        }
    }
}



