package com.strikerkk.aicommerce.user_service.controller;

import com.strikerkk.aicommerce.user_service.common.ApiResponse;
import com.strikerkk.aicommerce.user_service.dto.request.AddressRequest;
import com.strikerkk.aicommerce.user_service.dto.response.AddressResponse;
import com.strikerkk.aicommerce.user_service.service.AddressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @PostMapping("/address/add")
    public ResponseEntity<ApiResponse<AddressResponse>> addAddress(@Valid @RequestBody AddressRequest request) {

        AddressResponse addressResponse = addressService.addAddress(request);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Address added successfully", addressResponse));
    }

    @GetMapping("/address/all")
    public ResponseEntity<ApiResponse<List<AddressResponse>>> getAllAddresses() {

        List<AddressResponse> addressResponseList = addressService.allAddresses();

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Successfully fetch all addresses", addressResponseList));
    }

    @GetMapping("/address/{addressId}")
    public AddressResponse getAddressByAddressId(@PathVariable Long addressId) {
        return addressService.getAddressByAddressId(addressId);
    }

    @PutMapping("/address/update/{addressId}")
    public ResponseEntity<ApiResponse<AddressResponse>> updateAddress(@Valid @RequestBody AddressRequest request,
                                                                      @PathVariable Long addressId) {

        AddressResponse addressResponse = addressService.updateAddress(request, addressId);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Address updated successfully", addressResponse));
    }

    @DeleteMapping("/address/delete/{addressId}")
    public ResponseEntity<ApiResponse<Void>> deleteAddress(@PathVariable Long addressId) {

        addressService.deleteAddress(addressId);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Account deleted successfully"));
    }
}
