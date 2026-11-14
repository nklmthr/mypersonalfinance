package com.nklmthr.finance.personal.model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "transfer_splits")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferSplit {

    @org.hibernate.annotations.UuidGenerator
    @jakarta.persistence.Id
    @Column
    private String id;

    @Column(name = "source_transaction_id", nullable = false)
    private String sourceTransactionId;

    @Column(name = "dest_transaction_id", nullable = false)
    private String destTransactionId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(length = 500)
    private String explanation;
}
