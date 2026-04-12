package com.strikerkk.aicommerce.user_service.repository;

import com.strikerkk.aicommerce.user_service.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AddressRepository extends JpaRepository<Address, Long> {
}
