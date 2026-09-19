package com.strikerkk.aicommerce.user_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.strikerkk.aicommerce.user_service.dto.request.AddressRequest;
import com.strikerkk.aicommerce.user_service.exception.ResourceNotFoundException;
import com.strikerkk.aicommerce.user_service.exception.advice.GlobalExceptionHandler;
import com.strikerkk.aicommerce.user_service.service.AddressService;
import com.strikerkk.aicommerce.user_service.support.TestDataFactory;
import org.hamcrest.Matchers;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("AddressController")
class AddressControllerTest {

    private static final Long ADDRESS_ID = 10L;

    @Mock
    private AddressService addressService;

    @InjectMocks
    private AddressController addressController;

    @Captor
    private ArgumentCaptor<AddressRequest> addressRequestCaptor;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(addressController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ------------------------------------------------------------------ POST /address/add

    @Nested
    @DisplayName("POST /address/add")
    class AddAddress {

        @Test
        @DisplayName("returns 200 together with the created address")
        void shouldAddAddress() throws Exception {
            when(addressService.addAddress(any(AddressRequest.class)))
                    .thenReturn(TestDataFactory.addressResponse(ADDRESS_ID, true));

            mockMvc.perform(post("/address/add")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(TestDataFactory.addressRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Address added successfully"))
                    .andExpect(jsonPath("$.data.id").value(10))
                    .andExpect(jsonPath("$.data.city").value("Bengaluru"))
                    .andExpect(jsonPath("$.data.pinCode").value("560001"))
                    .andExpect(jsonPath("$.data.isDefault").value(true));
        }

        @Test
        @DisplayName("binds every field of the payload onto the request object")
        void shouldBindEveryField() throws Exception {
            when(addressService.addAddress(any(AddressRequest.class)))
                    .thenReturn(TestDataFactory.addressResponse(ADDRESS_ID, true));

            AddressRequest request = TestDataFactory.addressRequest();
            request.setIsDefault(true);

            mockMvc.perform(post("/address/add")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            verify(addressService).addAddress(addressRequestCaptor.capture());
            AddressRequest bound = addressRequestCaptor.getValue();
            assertThat(bound.getHouseNo()).isEqualTo("B-101");
            assertThat(bound.getStreet()).isEqualTo("MG Road");
            assertThat(bound.getCity()).isEqualTo("Bengaluru");
            assertThat(bound.getState()).isEqualTo("Karnataka");
            assertThat(bound.getCountry()).isEqualTo("India");
            assertThat(bound.getPinCode()).isEqualTo("560001");
            assertThat(bound.getIsDefault()).isTrue();
        }

        @Test
        @DisplayName("returns 400 when the pin code is malformed")
        void shouldRejectMalformedPinCode() throws Exception {
            AddressRequest request = TestDataFactory.addressRequest();
            request.setPinCode("12345");

            mockMvc.perform(post("/address/add")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("pinCode: Pin-code must be at least 6 digits"));

            verify(addressService, never()).addAddress(any(AddressRequest.class));
        }

        @Test
        @DisplayName("returns 400 when the city is blank")
        void shouldRejectBlankCity() throws Exception {
            AddressRequest request = TestDataFactory.addressRequest();
            request.setCity("   ");

            mockMvc.perform(post("/address/add")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("city: City name is required"));
        }

        @Test
        @DisplayName("returns 404 when the owner of the address cannot be found")
        void shouldReturnNotFoundWhenUserIsMissing() throws Exception {
            when(addressService.addAddress(any(AddressRequest.class)))
                    .thenThrow(new ResourceNotFoundException("User not found"));

            mockMvc.perform(post("/address/add")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(TestDataFactory.addressRequest())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("User not found"));
        }
    }

    // ------------------------------------------------------------------ GET /address/all

    @Nested
    @DisplayName("GET /address/all")
    class AllAddresses {

        @Test
        @DisplayName("returns 200 together with every address of the user")
        void shouldReturnAllAddresses() throws Exception {
            when(addressService.allAddresses()).thenReturn(List.of(
                    TestDataFactory.addressResponse(10L, true),
                    TestDataFactory.addressResponse(11L, false)));

            mockMvc.perform(get("/address/all"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Successfully fetch all addresses"))
                    .andExpect(jsonPath("$.data", Matchers.hasSize(2)))
                    .andExpect(jsonPath("$.data[0].id").value(10))
                    .andExpect(jsonPath("$.data[0].isDefault").value(true))
                    .andExpect(jsonPath("$.data[1].id").value(11))
                    .andExpect(jsonPath("$.data[1].isDefault").value(false));
        }

        @Test
        @DisplayName("returns 200 with an empty list when the user has no address")
        void shouldReturnEmptyList() throws Exception {
            when(addressService.allAddresses()).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/address/all"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", Matchers.hasSize(0)));
        }
    }

    // ----------------------------------------------------------- GET /address/{addressId}

    @Nested
    @DisplayName("GET /address/{addressId}")
    class AddressById {

        @Test
        @DisplayName("returns the raw address, without the ApiResponse envelope")
        void shouldReturnRawAddress() throws Exception {
            when(addressService.getAddressByAddressId(ADDRESS_ID))
                    .thenReturn(TestDataFactory.addressResponse(ADDRESS_ID, true));

            mockMvc.perform(get("/address/{addressId}", ADDRESS_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(10))
                    .andExpect(jsonPath("$.street").value("MG Road"))
                    .andExpect(jsonPath("$.isDefault").value(true))
                    .andExpect(jsonPath("$.success").doesNotExist());
        }

        @Test
        @DisplayName("returns 404 when the address does not belong to the user")
        void shouldReturnNotFound() throws Exception {
            when(addressService.getAddressByAddressId(ADDRESS_ID))
                    .thenThrow(new ResourceNotFoundException("Address not present"));

            mockMvc.perform(get("/address/{addressId}", ADDRESS_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Address not present"));
        }
    }

    // -------------------------------------------------- PUT /address/update/{addressId}

    @Nested
    @DisplayName("PUT /address/update/{addressId}")
    class UpdateAddress {

        @Test
        @DisplayName("returns 200 together with the updated address")
        void shouldUpdateAddress() throws Exception {
            when(addressService.updateAddress(any(AddressRequest.class), eq(ADDRESS_ID)))
                    .thenReturn(TestDataFactory.addressResponse(ADDRESS_ID, true));

            AddressRequest request = TestDataFactory.addressRequest();
            request.setIsDefault(true);

            mockMvc.perform(put("/address/update/{addressId}", ADDRESS_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Address updated successfully"))
                    .andExpect(jsonPath("$.data.isDefault").value(true));
        }

        @Test
        @DisplayName("returns 400 when the payload is invalid")
        void shouldRejectInvalidPayload() throws Exception {
            AddressRequest request = TestDataFactory.addressRequest();
            request.setStreet("");

            mockMvc.perform(put("/address/update/{addressId}", ADDRESS_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("street: Street Address name is required"));

            verify(addressService, never()).updateAddress(any(AddressRequest.class), anyLong());
        }

        @Test
        @DisplayName("returns 404 when the address does not exist")
        void shouldReturnNotFound() throws Exception {
            when(addressService.updateAddress(any(AddressRequest.class), eq(ADDRESS_ID)))
                    .thenThrow(new ResourceNotFoundException("Address not found"));

            mockMvc.perform(put("/address/update/{addressId}", ADDRESS_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(TestDataFactory.addressRequest())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Address not found"));
        }
    }

    // ----------------------------------------------- DELETE /address/delete/{addressId}

    @Nested
    @DisplayName("DELETE /address/delete/{addressId}")
    class DeleteAddress {

        @Test
        @DisplayName("returns 200 and an envelope without any payload")
        void shouldDeleteAddress() throws Exception {
            doNothing().when(addressService).deleteAddress(ADDRESS_ID);

            mockMvc.perform(delete("/address/delete/{addressId}", ADDRESS_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Account deleted successfully"))
                    .andExpect(jsonPath("$.data").doesNotExist());

            verify(addressService).deleteAddress(ADDRESS_ID);
        }

        @Test
        @DisplayName("returns 404 when the last default address cannot be removed")
        void shouldReturnNotFoundWhenLastDefaultAddress() throws Exception {
            doThrow(new ResourceNotFoundException("Please add another address"))
                    .when(addressService).deleteAddress(ADDRESS_ID);

            mockMvc.perform(delete("/address/delete/{addressId}", ADDRESS_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Please add another address"));
        }
    }
}



