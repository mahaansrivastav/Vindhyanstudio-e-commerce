/*
 * Copyright (c) 2026 Mahan Srivastava. All rights reserved.
 * Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
 */
package com.vindhyanstudio.customer;

import java.util.List;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.vindhyanstudio.common.ApiException;
import com.vindhyanstudio.common.CurrentUser;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/me")
public class ProfileController {

    private final CustomerRepository customers;
    private final AddressRepository addresses;

    public ProfileController(CustomerRepository customers, AddressRepository addresses) {
        this.customers = customers;
        this.addresses = addresses;
    }

    public record ProfileDto(Long id, String name, String email, String phone, boolean admin) {}

    public record ProfileUpdate(@Size(max = 120) String name) {}

    public record AddressDto(
            Long id,
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Pattern(regexp = "[6-9][0-9]{9}", message = "must be a 10-digit Indian mobile number") String phone,
            @NotBlank @Size(max = 250) String line1,
            @Size(max = 250) String line2,
            @NotBlank @Size(max = 100) String city,
            @NotBlank @Size(max = 100) String state,
            @NotBlank @Pattern(regexp = "[1-9][0-9]{5}", message = "must be a 6-digit pincode") String pincode,
            boolean isDefault) {

        static AddressDto of(Address a) {
            return new AddressDto(a.getId(), a.getName(), a.getPhone(), a.getLine1(), a.getLine2(),
                    a.getCity(), a.getState(), a.getPincode(), a.isDefault());
        }
    }

    @GetMapping
    ProfileDto me() {
        Customer c = customers.findById(CurrentUser.id()).orElseThrow(() -> ApiException.notFound("Account not found"));
        return new ProfileDto(c.getId(), c.getName(), c.getEmail(), c.getPhone(), CurrentUser.isAdmin());
    }

    @PutMapping
    @Transactional
    ProfileDto update(@Valid @RequestBody ProfileUpdate body) {
        Customer c = customers.findById(CurrentUser.id()).orElseThrow();
        c.setName(body.name() == null ? null : body.name().trim());
        return me();
    }

    @GetMapping("/addresses")
    List<AddressDto> listAddresses() {
        return addresses.findByCustomerIdAndDeletedFalseOrderByIdAsc(CurrentUser.id()).stream().map(AddressDto::of).toList();
    }

    @PostMapping("/addresses")
    @Transactional
    AddressDto addAddress(@Valid @RequestBody AddressDto body) {
        Address a = new Address();
        a.setCustomerId(CurrentUser.id());
        apply(a, body);
        List<Address> existing = addresses.findByCustomerIdAndDeletedFalseOrderByIdAsc(CurrentUser.id());
        if (existing.isEmpty()) a.setDefault(true);
        a = addresses.save(a);
        if (a.isDefault()) clearOtherDefaults(a);
        return AddressDto.of(a);
    }

    @PutMapping("/addresses/{id}")
    @Transactional
    AddressDto updateAddress(@PathVariable Long id, @Valid @RequestBody AddressDto body) {
        Address a = addresses.findByIdAndCustomerIdAndDeletedFalse(id, CurrentUser.id())
                .orElseThrow(() -> ApiException.notFound("Address not found"));
        apply(a, body);
        if (a.isDefault()) clearOtherDefaults(a);
        return AddressDto.of(a);
    }

    @DeleteMapping("/addresses/{id}")
    @Transactional
    void deleteAddress(@PathVariable Long id) {
        // Soft delete: old orders keep a snapshot, but the address list stays clean.
        addresses.findByIdAndCustomerIdAndDeletedFalse(id, CurrentUser.id()).ifPresent(a -> a.setDeleted(true));
    }

    private void apply(Address a, AddressDto d) {
        a.setName(d.name().trim());
        a.setPhone(d.phone());
        a.setLine1(d.line1().trim());
        a.setLine2(d.line2() == null ? null : d.line2().trim());
        a.setCity(d.city().trim());
        a.setState(d.state().trim());
        a.setPincode(d.pincode());
        a.setDefault(d.isDefault());
    }

    private void clearOtherDefaults(Address keep) {
        addresses.findByCustomerIdAndDeletedFalseOrderByIdAsc(keep.getCustomerId()).stream()
                .filter(o -> !o.getId().equals(keep.getId()))
                .forEach(o -> o.setDefault(false));
    }
}
