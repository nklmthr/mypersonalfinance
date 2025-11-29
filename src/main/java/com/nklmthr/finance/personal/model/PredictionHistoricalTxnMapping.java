package com.nklmthr.finance.personal.model;

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
@Table(name = "prediction_historical_txn_mapping",
       uniqueConstraints = @UniqueConstraint(columnNames = {"predicted_transaction_id", "historical_transaction_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class PredictionHistoricalTxnMapping {

	@Id
	@UuidGenerator
	@Column
	private String id;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "predicted_transaction_id", nullable = false)
	@ToString.Exclude
	private PredictedTransaction predictedTransaction;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "historical_transaction_id", nullable = false)
	@ToString.Exclude
	private AccountTransaction historicalTransaction;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
}

