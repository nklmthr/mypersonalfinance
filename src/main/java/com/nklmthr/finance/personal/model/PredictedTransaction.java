package com.nklmthr.finance.personal.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nklmthr.finance.personal.enums.TransactionType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "predicted_transactions",
       uniqueConstraints = @UniqueConstraint(columnNames = {"prediction_rule_id", "prediction_month"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class PredictedTransaction {

	@Id
	@UuidGenerator
	@Column
	private String id;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "app_user_id", nullable = false)
	@JsonIgnore
	@ToString.Exclude
	private AppUser appUser;

	@ManyToOne(optional = false)
	@JoinColumn(name = "prediction_rule_id", nullable = false)
	@ToString.Exclude
	private PredictionRule predictionRule;

	@ManyToOne(optional = false)
	@JoinColumn(name = "category_id", nullable = false)
	@ToString.Exclude
	private Category category;

	@ManyToOne
	@JoinColumn(name = "account_id")
	@ToString.Exclude
	private Account account;

	@Column(name = "predicted_amount", precision = 19, scale = 4, nullable = false)
	private BigDecimal predictedAmount;

	@Column(name = "remaining_amount", precision = 19, scale = 4, nullable = false)
	private BigDecimal remainingAmount;  // Decreases as actual transactions occur

	@Column(name = "actual_spent", precision = 19, scale = 4, nullable = false)
	@Builder.Default
	private BigDecimal actualSpent = BigDecimal.ZERO;  // Total actual spending against this prediction

	@Enumerated(EnumType.STRING)
	@Column(name = "transaction_type", nullable = false)
	private TransactionType transactionType;

	@Column(name = "prediction_month", nullable = false, length = 7)
	private String predictionMonth;  // Format: YYYY-MM

	@Column(nullable = false, length = 1000)
	private String description;

	@Column(length = 2000)
	private String explanation;

	@Column(length = 3)
	@Builder.Default
	private String currency = "INR";

	@Column(name = "calculation_date", nullable = false)
	private LocalDateTime calculationDate;

	@Column(name = "based_on_transaction_count", nullable = false)
	@Builder.Default
	private int basedOnTransactionCount = 0;

	@Column(name = "is_visible", nullable = false)
	@Builder.Default
	private boolean visible = true;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;
}

