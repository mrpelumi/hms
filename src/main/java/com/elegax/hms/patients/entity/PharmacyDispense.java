package com.elegax.hms.patients.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "pharmacy_dispenses")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PharmacyDispense {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long prescriptionId;
    private Long patientId;
    private Long inventoryItemId;
    private String medicationName;
    private String batchNumber;
    private Integer quantityDispensed;
    private BigDecimal unitPrice;
    private BigDecimal totalAmount;
    private String dispensedBy;
    @Column(columnDefinition = "text")
    private String pharmacistNote;
    private OffsetDateTime dispensedAt;

    @PrePersist
    public void prePersist() {
        dispensedAt = OffsetDateTime.now();
        if (quantityDispensed == null || quantityDispensed < 1) {
            quantityDispensed = 1;
        }
        if (unitPrice == null) {
            unitPrice = BigDecimal.ZERO;
        }
        if (totalAmount == null) {
            totalAmount = unitPrice.multiply(BigDecimal.valueOf(quantityDispensed));
        }
    }
}
