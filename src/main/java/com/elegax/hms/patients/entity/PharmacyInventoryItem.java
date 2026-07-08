package com.elegax.hms.patients.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "pharmacy_inventory_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PharmacyInventoryItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String medicationName;
    private String strength;
    private String dosageForm;
    private String batchNumber;
    private LocalDate expiryDate;
    private Integer quantityOnHand;
    private Integer reorderLevel;
    private BigDecimal unitCost;
    private BigDecimal sellingPrice;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
        if (quantityOnHand == null) {
            quantityOnHand = 0;
        }
        if (reorderLevel == null) {
            reorderLevel = 10;
        }
        if (unitCost == null) {
            unitCost = BigDecimal.ZERO;
        }
        if (sellingPrice == null) {
            sellingPrice = BigDecimal.ZERO;
        }
        if (status == null || status.isBlank()) {
            status = "ACTIVE";
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
