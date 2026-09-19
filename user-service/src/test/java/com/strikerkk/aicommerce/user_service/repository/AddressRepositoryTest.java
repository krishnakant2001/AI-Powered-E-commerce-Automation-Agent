package com.strikerkk.aicommerce.user_service.repository;

import com.strikerkk.aicommerce.user_service.entity.Address;
import com.strikerkk.aicommerce.user_service.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@DisplayName("AddressRepository")
class AddressRepositoryTest {

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User owner;
    private User otherUser;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(user("owner@example.com"));
        otherUser = userRepository.save(user("other@example.com"));
        entityManager.flush();
    }

    private User user(String email) {
        return User.builder()
                .firstName("Krishnakant")
                .email(email)
                .password("encoded-password")
                .addresses(new ArrayList<>())
                .build();
    }

    private Address address(User user, String city, boolean isDefault) {
        return Address.builder()
                .user(user)
                .houseNo("B-101")
                .street("MG Road")
                .city(city)
                .state("Karnataka")
                .country("India")
                .pinCode("560001")
                .isDefault(isDefault)
                .build();
    }

    @Test
    @DisplayName("persists an address and generates its id")
    void shouldPersistAddress() {
        Address saved = addressRepository.saveAndFlush(address(owner, "Bengaluru", true));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getIsDefault()).isTrue();
        assertThat(saved.getUser().getId()).isEqualTo(owner.getId());
    }

    @Test
    @DisplayName("rejects an address that is not attached to a user")
    void shouldRejectAddressWithoutUser() {
        Address orphan = address(null, "Bengaluru", false);

        assertThatThrownBy(() -> addressRepository.saveAndFlush(orphan))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("findByIdAndUserId returns the address of its owner")
    void shouldFindByIdAndUserId() {
        Address saved = addressRepository.saveAndFlush(address(owner, "Bengaluru", true));

        Optional<Address> found = addressRepository.findByIdAndUserId(saved.getId(), owner.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getCity()).isEqualTo("Bengaluru");
    }

    @Test
    @DisplayName("findByIdAndUserId does not leak the address of another user")
    void shouldNotLeakAddressOfAnotherUser() {
        Address saved = addressRepository.saveAndFlush(address(owner, "Bengaluru", true));

        assertThat(addressRepository.findByIdAndUserId(saved.getId(), otherUser.getId())).isEmpty();
    }

    @Test
    @DisplayName("findByIdAndUserId is empty for an unknown address")
    void shouldReturnEmptyForUnknownAddress() {
        assertThat(addressRepository.findByIdAndUserId(-1L, owner.getId())).isEmpty();
    }

    @Test
    @DisplayName("findAllByUserId returns only the addresses of the given user")
    void shouldFindAllByUserId() {
        addressRepository.save(address(owner, "Bengaluru", true));
        addressRepository.save(address(owner, "Mysuru", false));
        addressRepository.save(address(otherUser, "Delhi", true));
        entityManager.flush();

        List<Address> addresses = addressRepository.findAllByUserId(owner.getId());

        assertThat(addresses).hasSize(2);
        assertThat(addresses).extracting(Address::getCity)
                .containsExactlyInAnyOrder("Bengaluru", "Mysuru");
    }

    @Test
    @DisplayName("findAllByUserId returns an empty list when the user has no address")
    void shouldReturnEmptyListForUserWithoutAddress() {
        assertThat(addressRepository.findAllByUserId(owner.getId())).isEmpty();
    }

    @Test
    @DisplayName("findFirstByUserIdAndIdNot returns another address of the same user")
    void shouldFindAnotherAddressOfTheSameUser() {
        Address first = addressRepository.save(address(owner, "Bengaluru", true));
        Address second = addressRepository.save(address(owner, "Mysuru", false));
        entityManager.flush();

        Optional<Address> found = addressRepository.findFirstByUserIdAndIdNot(owner.getId(), first.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(second.getId());
    }

    @Test
    @DisplayName("findFirstByUserIdAndIdNot is empty when the user owns a single address")
    void shouldReturnEmptyWhenUserOwnsASingleAddress() {
        Address only = addressRepository.saveAndFlush(address(owner, "Bengaluru", true));

        assertThat(addressRepository.findFirstByUserIdAndIdNot(owner.getId(), only.getId())).isEmpty();
    }

    @Test
    @DisplayName("findFirstByUserIdAndIdNot never returns the address of another user")
    void shouldNotReturnAddressOfAnotherUser() {
        Address ownerAddress = addressRepository.save(address(owner, "Bengaluru", true));
        addressRepository.save(address(otherUser, "Delhi", true));
        entityManager.flush();

        assertThat(addressRepository.findFirstByUserIdAndIdNot(owner.getId(), ownerAddress.getId())).isEmpty();
    }

    @Test
    @DisplayName("existsByUserId reports whether the user already owns an address")
    void shouldReportWhetherUserOwnsAnAddress() {
        assertThat(addressRepository.existsByUserId(owner.getId())).isFalse();

        addressRepository.saveAndFlush(address(owner, "Bengaluru", true));

        assertThat(addressRepository.existsByUserId(owner.getId())).isTrue();
        assertThat(addressRepository.existsByUserId(otherUser.getId())).isFalse();
    }

    @Test
    @DisplayName("resetOtherAddresses clears the default flag of every other address of the user")
    void shouldResetOtherAddresses() {
        Address keep = addressRepository.save(address(owner, "Bengaluru", false));
        Address firstOther = addressRepository.save(address(owner, "Mysuru", true));
        Address secondOther = addressRepository.save(address(owner, "Hubballi", true));
        entityManager.flush();

        addressRepository.resetOtherAddresses(owner.getId(), keep.getId());
        entityManager.clear();

        assertThat(addressRepository.findById(firstOther.getId()).orElseThrow().getIsDefault()).isFalse();
        assertThat(addressRepository.findById(secondOther.getId()).orElseThrow().getIsDefault()).isFalse();
        assertThat(addressRepository.findById(keep.getId()).orElseThrow().getIsDefault()).isFalse();
    }

    @Test
    @DisplayName("resetOtherAddresses never touches the address that must stay the default one")
    void shouldNotResetTheKeptAddress() {
        Address keep = addressRepository.save(address(owner, "Bengaluru", true));
        Address other = addressRepository.save(address(owner, "Mysuru", true));
        entityManager.flush();

        addressRepository.resetOtherAddresses(owner.getId(), keep.getId());
        entityManager.clear();

        assertThat(addressRepository.findById(keep.getId()).orElseThrow().getIsDefault()).isTrue();
        assertThat(addressRepository.findById(other.getId()).orElseThrow().getIsDefault()).isFalse();
    }

    @Test
    @DisplayName("resetOtherAddresses never touches the addresses of another user")
    void shouldNotResetAddressesOfAnotherUser() {
        Address keep = addressRepository.save(address(owner, "Bengaluru", true));
        Address foreign = addressRepository.save(address(otherUser, "Delhi", true));
        entityManager.flush();

        addressRepository.resetOtherAddresses(owner.getId(), keep.getId());
        entityManager.clear();

        assertThat(addressRepository.findById(foreign.getId()).orElseThrow().getIsDefault()).isTrue();
    }

    @Test
    @DisplayName("deletes an address without deleting its owner")
    void shouldDeleteAddressOnly() {
        Address saved = addressRepository.saveAndFlush(address(owner, "Bengaluru", true));

        addressRepository.delete(saved);
        entityManager.flush();
        entityManager.clear();

        assertThat(addressRepository.findById(saved.getId())).isEmpty();
        assertThat(userRepository.findById(owner.getId())).isPresent();
    }
}

