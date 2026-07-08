package com.elegax.hms.patients.repository;

import com.elegax.hms.patients.entity.PharmacyInventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PharmacyInventoryItemRepository extends JpaRepository<PharmacyInventoryItem, Long> {
    List<PharmacyInventoryItem> findByStatusOrderByMedicationNameAsc(String status);
}
