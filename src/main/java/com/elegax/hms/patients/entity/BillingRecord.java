package com.elegax.hms.patients.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "billing_records")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long patientId;
    private Long sourceId;
    private String sourceType;
    private String description;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalAmount;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime paidAt;

    @PrePersist
    public void prePersist() {
        createdAt = OffsetDateTime.now();
        if (quantity == null || quantity < 1) {
            quantity = 1;
        }
        if (unitPrice == null) {
            unitPrice = BigDecimal.ZERO;
        }
        if (totalAmount == null) {
            totalAmount = unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
        if (status == null) {
            status = "UNPAID";
        }
    }
}
