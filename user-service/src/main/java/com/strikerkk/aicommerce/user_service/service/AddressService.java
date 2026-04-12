package com.strikerkk.aicommerce.user_service.service;

import com.strikerkk.aicommerce.user_service.dto.request.AddNewAddressRequest;
import com.strikerkk.aicommerce.user_service.dto.response.AddressResponse;
import com.strikerkk.aicommerce.user_service.entity.Address;
import com.strikerkk.aicommerce.user_service.entity.User;
import com.strikerkk.aicommerce.user_service.repository.AddressRepository;
import com.strikerkk.aicommerce.user_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class AddressService {

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final ModelMapper modelMapper;

    public AddressResponse addAddress(AddNewAddressRequest request, String userId) {

        User user = userRepository.findById(Long.valueOf(userId))
                .orElseThrow(() -> new RuntimeException("User not found"));

        //Adding new address
        Address newAddress = Address.builder()
                .user(user)
                .houseNo(request.getHouseNo())
                .street(request.getStreet())
                .city(request.getCity())
                .state(request.getState())
                .country(request.getCountry())
                .pinCode(request.getPinCode())
                .isDefault(request.getIsDefault() != null ? request.getIsDefault() : false)
                .build();

        Address savedNewAddress = addressRepository.save(newAddress);

        return modelMapper.map(savedNewAddress, AddressResponse.class);
    }


}
