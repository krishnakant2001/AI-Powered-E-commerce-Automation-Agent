package com.strikerkk.aicommerce.user_service.repository;

import com.strikerkk.aicommerce.user_service.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface AddressRepository extends JpaRepository<Address, Long> {

    Optional<Address> findByIdAndUserId(Long addressId, Long userId);

    List<Address> findAllByUserId(Long userId);

    Optional<Address> findFirstByUserIdAndIdNot(Long userId, Long addressId);

    @Modifying
    @Transactional
    @Query("""
        UPDATE Address a
        SET a.isDefault = false
        WHERE a.user.id = :userId AND a.id != :addressId
    """)
    void resetOtherAddresses(Long userId, Long addressId);
}
