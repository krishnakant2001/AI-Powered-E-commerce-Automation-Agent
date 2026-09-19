package com.strikerkk.aicommerce.user_service.service;

import com.strikerkk.aicommerce.user_service.auth.UserContext;
import com.strikerkk.aicommerce.user_service.dto.request.AddressRequest;
import com.strikerkk.aicommerce.user_service.dto.response.AddressResponse;
import com.strikerkk.aicommerce.user_service.entity.Address;
import com.strikerkk.aicommerce.user_service.entity.User;
import com.strikerkk.aicommerce.user_service.exception.ResourceNotFoundException;
import com.strikerkk.aicommerce.user_service.repository.AddressRepository;
import com.strikerkk.aicommerce.user_service.repository.UserRepository;
import com.strikerkk.aicommerce.user_service.support.TestDataFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AddressService")
class AddressServiceTest {

    private static final Long ADDRESS_ID = 10L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private ModelMapper modelMapper;

    @InjectMocks
    private AddressService addressService;

    @Captor
    private ArgumentCaptor<Address> addressCaptor;

    private User user;
    private AddressRequest request;

    @BeforeEach
    void setUp() {
        UserContext.setUserId(TestDataFactory.USER_ID_AS_STRING);
        user = TestDataFactory.user();
        request = TestDataFactory.addressRequest();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    // addAddress --------------------------------------------------------------------------

    @Nested
    @DisplayName("addAddress()")
    class AddAddress {

        @Test
        @DisplayName("marks the very first address of a user as the default one")
        void shouldMarkFirstAddressAsDefault() {
            Address saved = TestDataFactory.address(ADDRESS_ID, user, true);
            AddressResponse expected = TestDataFactory.addressResponse(ADDRESS_ID, true);

            when(userRepository.findById(TestDataFactory.USER_ID)).thenReturn(Optional.of(user));
            when(addressRepository.existsByUserId(TestDataFactory.USER_ID)).thenReturn(false);
            when(addressRepository.save(any(Address.class))).thenReturn(saved);
            when(modelMapper.map(saved, AddressResponse.class)).thenReturn(expected);

            AddressResponse result = addressService.addAddress(request);

            assertThat(result).isSameAs(expected);

            verify(addressRepository).save(addressCaptor.capture());
            Address captured = addressCaptor.getValue();
            assertThat(captured.getIsDefault()).isTrue();
            assertThat(captured.getUser()).isSameAs(user);
            assertThat(captured.getHouseNo()).isEqualTo("B-101");
            assertThat(captured.getStreet()).isEqualTo("MG Road");
            assertThat(captured.getCity()).isEqualTo("Bengaluru");
            assertThat(captured.getState()).isEqualTo("Karnataka");
            assertThat(captured.getCountry()).isEqualTo("India");
            assertThat(captured.getPinCode()).isEqualTo("560001");
        }

        @Test
        @DisplayName("does not mark an address as default when the user already has one")
        void shouldNotMarkSubsequentAddressAsDefault() {
            Address saved = TestDataFactory.address(ADDRESS_ID, user, false);
            AddressResponse expected = TestDataFactory.addressResponse(ADDRESS_ID, false);

            when(userRepository.findById(TestDataFactory.USER_ID)).thenReturn(Optional.of(user));
            when(addressRepository.existsByUserId(TestDataFactory.USER_ID)).thenReturn(true);
            when(addressRepository.save(any(Address.class))).thenReturn(saved);
            when(modelMapper.map(saved, AddressResponse.class)).thenReturn(expected);

            AddressResponse result = addressService.addAddress(request);

            assertThat(result.getIsDefault()).isFalse();

            verify(addressRepository).save(addressCaptor.capture());
            assertThat(addressCaptor.getValue().getIsDefault()).isFalse();
        }

        @Test
        @DisplayName("ignores the isDefault flag coming from the request")
        void shouldIgnoreIsDefaultFromRequest() {
            request.setIsDefault(true);
            Address saved = TestDataFactory.address(ADDRESS_ID, user, false);

            when(userRepository.findById(TestDataFactory.USER_ID)).thenReturn(Optional.of(user));
            when(addressRepository.existsByUserId(TestDataFactory.USER_ID)).thenReturn(true);
            when(addressRepository.save(any(Address.class))).thenReturn(saved);
            when(modelMapper.map(saved, AddressResponse.class))
                    .thenReturn(TestDataFactory.addressResponse(ADDRESS_ID, false));

            addressService.addAddress(request);

            verify(addressRepository).save(addressCaptor.capture());
            assertThat(addressCaptor.getValue().getIsDefault()).isFalse();
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when the user does not exist")
        void shouldThrowWhenUserIsMissing() {
            when(userRepository.findById(TestDataFactory.USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> addressService.addAddress(request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("User not found");

            verifyNoInteractions(addressRepository);
            verifyNoInteractions(modelMapper);
        }

        @Test
        @DisplayName("throws NumberFormatException when the UserContext is empty")
        void shouldThrowWhenUserContextIsEmpty() {
            UserContext.clear();

            assertThatThrownBy(() -> addressService.addAddress(request))
                    .isInstanceOf(NumberFormatException.class);

            verifyNoInteractions(userRepository);
        }
    }

    // getAddressByAddressId -------------------------------------------------------------

    @Nested
    @DisplayName("getAddressByAddressId()")
    class GetAddressByAddressId {

        @Test
        @DisplayName("returns the address when it belongs to the current user")
        void shouldReturnAddress() {
            Address address = TestDataFactory.address(ADDRESS_ID, user, true);
            AddressResponse expected = TestDataFactory.addressResponse(ADDRESS_ID, true);

            when(addressRepository.findByIdAndUserId(ADDRESS_ID, TestDataFactory.USER_ID))
                    .thenReturn(Optional.of(address));
            when(modelMapper.map(address, AddressResponse.class)).thenReturn(expected);

            AddressResponse result = addressService.getAddressByAddressId(ADDRESS_ID);

            assertThat(result).isSameAs(expected);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when the address is missing or owned by somebody else")
        void shouldThrowWhenAddressIsMissing() {
            when(addressRepository.findByIdAndUserId(ADDRESS_ID, TestDataFactory.USER_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> addressService.getAddressByAddressId(ADDRESS_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Address not present");
        }
    }

    // allAddresses -----------------------------------------------------------------------

    @Nested
    @DisplayName("allAddresses()")
    class AllAddresses {

        @Test
        @DisplayName("returns every address that belongs to the current user")
        void shouldReturnAllAddresses() {
            Address first = TestDataFactory.address(10L, user, true);
            Address second = TestDataFactory.address(11L, user, false);
            AddressResponse firstResponse = TestDataFactory.addressResponse(10L, true);
            AddressResponse secondResponse = TestDataFactory.addressResponse(11L, false);

            when(addressRepository.findAllByUserId(TestDataFactory.USER_ID))
                    .thenReturn(List.of(first, second));
            when(modelMapper.map(first, AddressResponse.class)).thenReturn(firstResponse);
            when(modelMapper.map(second, AddressResponse.class)).thenReturn(secondResponse);

            List<AddressResponse> result = addressService.allAddresses();

            assertThat(result).containsExactly(firstResponse, secondResponse);
        }

        @Test
        @DisplayName("returns an empty list when the user has no address")
        void shouldReturnEmptyList() {
            when(addressRepository.findAllByUserId(TestDataFactory.USER_ID)).thenReturn(List.of());

            assertThat(addressService.allAddresses()).isEmpty();
            verifyNoInteractions(modelMapper);
        }
    }

    // updateAddress -----------------------------------------------------------------------

    @Nested
    @DisplayName("updateAddress()")
    class UpdateAddress {

        @Test
        @DisplayName("updates every editable field of the address")
        void shouldUpdateAddressFields() {
            Address address = TestDataFactory.address(ADDRESS_ID, user, false);
            request.setHouseNo("C-202");
            request.setStreet("Brigade Road");
            request.setCity("Mysuru");
            request.setState("Karnataka");
            request.setCountry("India");
            request.setPinCode("570001");

            when(addressRepository.findByIdAndUserId(ADDRESS_ID, TestDataFactory.USER_ID))
                    .thenReturn(Optional.of(address));
            when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));
            when(modelMapper.map(any(Address.class), eq(AddressResponse.class)))
                    .thenReturn(TestDataFactory.addressResponse(ADDRESS_ID, false));

            addressService.updateAddress(request, ADDRESS_ID);

            verify(addressRepository).save(addressCaptor.capture());
            Address saved = addressCaptor.getValue();
            assertThat(saved.getHouseNo()).isEqualTo("C-202");
            assertThat(saved.getStreet()).isEqualTo("Brigade Road");
            assertThat(saved.getCity()).isEqualTo("Mysuru");
            assertThat(saved.getState()).isEqualTo("Karnataka");
            assertThat(saved.getCountry()).isEqualTo("India");
            assertThat(saved.getPinCode()).isEqualTo("570001");
        }

        @Test
        @DisplayName("promotes the address to default and resets the other addresses")
        void shouldPromoteAddressToDefault() {
            Address address = TestDataFactory.address(ADDRESS_ID, user, false);
            request.setIsDefault(true);

            when(addressRepository.findByIdAndUserId(ADDRESS_ID, TestDataFactory.USER_ID))
                    .thenReturn(Optional.of(address));
            when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));
            when(modelMapper.map(any(Address.class), eq(AddressResponse.class)))
                    .thenReturn(TestDataFactory.addressResponse(ADDRESS_ID, true));

            addressService.updateAddress(request, ADDRESS_ID);

            verify(addressRepository).resetOtherAddresses(TestDataFactory.USER_ID, ADDRESS_ID);
            verify(addressRepository).save(addressCaptor.capture());
            assertThat(addressCaptor.getValue().getIsDefault()).isTrue();
        }

        @Test
        @DisplayName("demotes the address when isDefault is explicitly false")
        void shouldDemoteAddressWhenIsDefaultIsFalse() {
            Address address = TestDataFactory.address(ADDRESS_ID, user, true);
            request.setIsDefault(false);

            when(addressRepository.findByIdAndUserId(ADDRESS_ID, TestDataFactory.USER_ID))
                    .thenReturn(Optional.of(address));
            when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));
            when(modelMapper.map(any(Address.class), eq(AddressResponse.class)))
                    .thenReturn(TestDataFactory.addressResponse(ADDRESS_ID, false));

            addressService.updateAddress(request, ADDRESS_ID);

            verify(addressRepository, never()).resetOtherAddresses(anyLong(), anyLong());
            verify(addressRepository).save(addressCaptor.capture());
            assertThat(addressCaptor.getValue().getIsDefault()).isFalse();
        }

        @Test
        @DisplayName("keeps the current default flag when isDefault is not supplied")
        void shouldKeepDefaultFlagWhenIsDefaultIsNull() {
            Address address = TestDataFactory.address(ADDRESS_ID, user, true);
            request.setIsDefault(null);

            when(addressRepository.findByIdAndUserId(ADDRESS_ID, TestDataFactory.USER_ID))
                    .thenReturn(Optional.of(address));
            when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));
            when(modelMapper.map(any(Address.class), eq(AddressResponse.class)))
                    .thenReturn(TestDataFactory.addressResponse(ADDRESS_ID, true));

            addressService.updateAddress(request, ADDRESS_ID);

            verify(addressRepository, never()).resetOtherAddresses(anyLong(), anyLong());
            verify(addressRepository).save(addressCaptor.capture());
            assertThat(addressCaptor.getValue().getIsDefault()).isTrue();
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when the address is missing")
        void shouldThrowWhenAddressIsMissing() {
            when(addressRepository.findByIdAndUserId(ADDRESS_ID, TestDataFactory.USER_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> addressService.updateAddress(request, ADDRESS_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Address not found");

            verify(addressRepository, never()).save(any(Address.class));
        }
    }

    // deleteAddress -----------------------------------------------------------------------

    @Nested
    @DisplayName("deleteAddress()")
    class DeleteAddress {

        @Test
        @DisplayName("deletes a non default address without touching the other ones")
        void shouldDeleteNonDefaultAddress() {
            Address address = TestDataFactory.address(ADDRESS_ID, user, false);

            when(addressRepository.findByIdAndUserId(ADDRESS_ID, TestDataFactory.USER_ID))
                    .thenReturn(Optional.of(address));

            addressService.deleteAddress(ADDRESS_ID);

            verify(addressRepository, never()).findFirstByUserIdAndIdNot(anyLong(), anyLong());
            verify(addressRepository, never()).save(any(Address.class));
            verify(addressRepository).delete(address);
        }

        @Test
        @DisplayName("promotes another address to default before deleting the default one")
        void shouldPromoteAnotherAddressWhenDeletingDefault() {
            Address defaultAddress = TestDataFactory.address(ADDRESS_ID, user, true);
            Address anotherAddress = TestDataFactory.address(11L, user, false);

            when(addressRepository.findByIdAndUserId(ADDRESS_ID, TestDataFactory.USER_ID))
                    .thenReturn(Optional.of(defaultAddress));
            when(addressRepository.findFirstByUserIdAndIdNot(TestDataFactory.USER_ID, ADDRESS_ID))
                    .thenReturn(Optional.of(anotherAddress));

            addressService.deleteAddress(ADDRESS_ID);

            assertThat(anotherAddress.getIsDefault()).isTrue();
            verify(addressRepository).save(anotherAddress);
            verify(addressRepository).delete(defaultAddress);
        }

        @Test
        @DisplayName("refuses to delete the last remaining default address")
        void shouldRefuseToDeleteTheOnlyDefaultAddress() {
            Address defaultAddress = TestDataFactory.address(ADDRESS_ID, user, true);

            when(addressRepository.findByIdAndUserId(ADDRESS_ID, TestDataFactory.USER_ID))
                    .thenReturn(Optional.of(defaultAddress));
            when(addressRepository.findFirstByUserIdAndIdNot(TestDataFactory.USER_ID, ADDRESS_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> addressService.deleteAddress(ADDRESS_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Please add another address");

            verify(addressRepository, never()).delete(any(Address.class));
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when the address is missing")
        void shouldThrowWhenAddressIsMissing() {
            when(addressRepository.findByIdAndUserId(ADDRESS_ID, TestDataFactory.USER_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> addressService.deleteAddress(ADDRESS_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Address not found");

            verify(addressRepository, never()).delete(any(Address.class));
        }
    }
}

