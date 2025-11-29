package com.nklmthr.finance.personal.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "prediction_actual_txn_mapping",
       uniqueConstraints = @UniqueConstraint(columnNames = {"predicted_transaction_id", "actual_transaction_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class PredictionActualTxnMapping {

	@Id
	@UuidGenerator
	@Column
	private String id;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "predicted_transaction_id", nullable = false)
	@ToString.Exclude
	private PredictedTransaction predictedTransaction;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "actual_transaction_id", nullable = false)
	@ToString.Exclude
	private AccountTransaction actualTransaction;

	@Column(name = "amount_applied", precision = 19, scale = 4, nullable = false)
	private BigDecimal amountApplied;  // How much of this transaction was applied to the prediction

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
}

