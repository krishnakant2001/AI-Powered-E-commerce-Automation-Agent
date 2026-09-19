package com.strikerkk.aicommerce.user_service.repository;

import com.strikerkk.aicommerce.user_service.entity.Address;
import com.strikerkk.aicommerce.user_service.entity.User;
import com.strikerkk.aicommerce.user_service.entity.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@DisplayName("UserRepository")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User newUser(String email, String googleId) {
        return User.builder()
                .firstName("Krishnakant")
                .lastName("Nagvanshi")
                .email(email)
                .password("encoded-password")
                .phoneNumber("9876543210")
                .googleId(googleId)
                .addresses(new ArrayList<>())
                .build();
    }

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        entityManager.flush();
    }

    @Test
    @DisplayName("persists a user, generates its id and stamps the audit columns")
    void shouldPersistUser() {
        User saved = userRepository.saveAndFlush(newUser("krishna@example.com", null));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("findByEmail returns the matching user")
    void shouldFindByEmail() {
        userRepository.saveAndFlush(newUser("krishna@example.com", null));

        Optional<User> found = userRepository.findByEmail("krishna@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getFirstName()).isEqualTo("Krishnakant");
    }

    @Test
    @DisplayName("findByEmail is empty for an unknown email")
    void shouldNotFindUnknownEmail() {
        assertThat(userRepository.findByEmail("unknown@example.com")).isEmpty();
    }

    @Test
    @DisplayName("findByEmail is case sensitive - the service normalizes the email beforehand")
    void findByEmailShouldBeCaseSensitive() {
        userRepository.saveAndFlush(newUser("krishna@example.com", null));

        assertThat(userRepository.findByEmail("KRISHNA@EXAMPLE.COM")).isEmpty();
    }

    @Test
    @DisplayName("findByGoogleId returns the matching user")
    void shouldFindByGoogleId() {
        userRepository.saveAndFlush(newUser("krishna@example.com", "google-sub-123"));

        Optional<User> found = userRepository.findByGoogleId("google-sub-123");

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("krishna@example.com");
    }

    @Test
    @DisplayName("findByGoogleId is empty for an account that never used Google")
    void shouldNotFindMissingGoogleId() {
        userRepository.saveAndFlush(newUser("krishna@example.com", null));

        assertThat(userRepository.findByGoogleId("google-sub-123")).isEmpty();
    }

    @Test
    @DisplayName("rejects two users sharing the same email")
    void shouldRejectDuplicateEmail() {
        userRepository.saveAndFlush(newUser("krishna@example.com", null));

        assertThatThrownBy(() -> userRepository.saveAndFlush(newUser("krishna@example.com", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("rejects two users sharing the same Google id")
    void shouldRejectDuplicateGoogleId() {
        userRepository.saveAndFlush(newUser("first@example.com", "google-sub-123"));

        assertThatThrownBy(() -> userRepository.saveAndFlush(newUser("second@example.com", "google-sub-123")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("rejects a user without an email")
    void shouldRejectUserWithoutEmail() {
        User user = newUser(null, null);

        assertThatThrownBy(() -> userRepository.saveAndFlush(user))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("cascades the persistence of the addresses of a user")
    void shouldCascadeAddressPersistence() {
        User user = newUser("krishna@example.com", null);
        Address address = Address.builder()
                .user(user)
                .houseNo("B-101")
                .street("MG Road")
                .city("Bengaluru")
                .state("Karnataka")
                .country("India")
                .pinCode("560001")
                .isDefault(true)
                .build();
        user.getAddresses().add(address);

        User saved = userRepository.saveAndFlush(user);
        entityManager.clear();

        User reloaded = userRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getAddresses()).hasSize(1);
        assertThat(reloaded.getAddresses().get(0).getCity()).isEqualTo("Bengaluru");
    }

    @Test
    @DisplayName("removes the orphan addresses of a user")
    void shouldRemoveOrphanAddresses() {
        User user = newUser("krishna@example.com", null);
        user.getAddresses().add(Address.builder()
                .user(user)
                .street("MG Road")
                .city("Bengaluru")
                .state("Karnataka")
                .country("India")
                .pinCode("560001")
                .build());

        User saved = userRepository.saveAndFlush(user);
        entityManager.clear();

        User reloaded = userRepository.findById(saved.getId()).orElseThrow();
        reloaded.getAddresses().clear();
        userRepository.saveAndFlush(reloaded);
        entityManager.clear();

        assertThat(userRepository.findById(saved.getId()).orElseThrow().getAddresses()).isEmpty();
        assertThat(entityManager.getEntityManager()
                .createQuery("SELECT COUNT(a) FROM Address a", Long.class)
                .getSingleResult()).isZero();
    }

    @Test
    @DisplayName("deletes a user together with its addresses")
    void shouldDeleteUserWithItsAddresses() {
        User user = newUser("krishna@example.com", null);
        user.getAddresses().add(Address.builder()
                .user(user)
                .street("MG Road")
                .city("Bengaluru")
                .state("Karnataka")
                .country("India")
                .pinCode("560001")
                .build());
        User saved = userRepository.saveAndFlush(user);
        entityManager.clear();

        userRepository.deleteById(saved.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(userRepository.findById(saved.getId())).isEmpty();
        assertThat(entityManager.getEntityManager()
                .createQuery("SELECT COUNT(a) FROM Address a", Long.class)
                .getSingleResult()).isZero();
    }

    @Test
    @DisplayName("refreshes updatedAt when the user is modified")
    void shouldRefreshUpdatedAtOnModification() {
        User saved = userRepository.saveAndFlush(newUser("krishna@example.com", null));
        java.time.LocalDateTime createdAt = saved.getCreatedAt();

        saved.setFirstName("Updated");
        User updated = userRepository.saveAndFlush(saved);

        assertThat(updated.getCreatedAt()).isEqualTo(createdAt);
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(createdAt);
    }
}

