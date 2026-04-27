package com.strikerkk.aicommerce.user_service.service;

import com.strikerkk.aicommerce.user_service.auth.UserContext;
import com.strikerkk.aicommerce.user_service.dto.request.AddressRequest;
import com.strikerkk.aicommerce.user_service.dto.response.AddressResponse;
import com.strikerkk.aicommerce.user_service.entity.Address;
import com.strikerkk.aicommerce.user_service.entity.User;
import com.strikerkk.aicommerce.user_service.exception.ResourceNotFoundException;
import com.strikerkk.aicommerce.user_service.repository.AddressRepository;
import com.strikerkk.aicommerce.user_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class AddressService {

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final ModelMapper modelMapper;

    public AddressResponse addAddress(AddressRequest request) {

        Long userId = Long.valueOf(UserContext.getUserId());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        log.info("Adding new address for userId={}", userId);

        // Check if any address found for this user
        boolean firstAddress = addressRepository.findAllByUserId(userId).isEmpty();

        // Adding new address
        Address newAddress = Address.builder()
                .user(user)
                .houseNo(request.getHouseNo())
                .street(request.getStreet())
                .city(request.getCity())
                .state(request.getState())
                .country(request.getCountry())
                .pinCode(request.getPinCode())
                .isDefault(firstAddress)
                .build();

        Address savedNewAddress = addressRepository.save(newAddress);

        return modelMapper.map(savedNewAddress, AddressResponse.class);
    }

    public AddressResponse getAddressByAddressId(Long addressId) {
        Long userId = Long.valueOf(UserContext.getUserId());

        Address address = addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not present"));

        log.info("Fetching address details for userId={} with addressId={}", userId, addressId);

        return modelMapper.map(address, AddressResponse.class);

    }

    public List<AddressResponse> allAddresses() {

        Long userId = Long.valueOf(UserContext.getUserId());

        log.info("Getting all addresses for userId={}", userId);

        List<Address> addressList = addressRepository.findAllByUserId(userId);

        return addressList
                .stream()
                .map(address -> modelMapper.map(address, AddressResponse.class))
                .toList();
    }


    public AddressResponse updateAddress(AddressRequest request, Long addressId) {

        String userId = UserContext.getUserId();

        Address address = addressRepository.findByIdAndUserId(addressId, Long.valueOf(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));

        log.info("Updating address details for userId={} with addressId={}", userId, addressId);

        address.setHouseNo(request.getHouseNo());
        address.setStreet(request.getStreet());
        address.setCity(request.getCity());
        address.setState(request.getState());
        address.setCountry(request.getCountry());
        address.setPinCode(request.getPinCode());

        // If users update this address to default, set all others for this user to false
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            addressRepository.resetOtherAddresses(Long.valueOf(userId), addressId);
            address.setIsDefault(true);

        } else if (request.getIsDefault() != null) {
            address.setIsDefault(false);
        }

        Address updatedAddress = addressRepository.save(address);

        return modelMapper.map(updatedAddress, AddressResponse.class);
    }


    public void deleteAddress(Long addressId) {

        String userId = UserContext.getUserId();

        Address address = addressRepository.findByIdAndUserId(addressId, Long.valueOf(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));

        log.info("Deleting address for userId={} with addressId={}", userId, addressId);

        // If the address is default
        if (Boolean.TRUE.equals(address.getIsDefault())) {

            // Find another address of the user
            Address anotherAddress = addressRepository.findFirstByUserIdAndIdNot(Long.valueOf(userId), addressId)
                    .orElseThrow(() -> new ResourceNotFoundException("Please add another address"));

            // If another address exists, make it default
            anotherAddress.setIsDefault(true);
            addressRepository.save(anotherAddress);
        }

        addressRepository.delete(address);
    }
}
