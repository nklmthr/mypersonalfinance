package com.nklmthr.finance.personal.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nklmthr.finance.personal.enums.RecurrenceType;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "recurring_transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class RecurringTransaction {

	@Id
	@UuidGenerator
	@Column
	private String id;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "app_user_id", nullable = false)
	@JsonIgnore
	@ToString.Exclude
	private AppUser appUser;

	@Column(nullable = false, length = 500)
	private String description;

	@ManyToOne(optional = false)
	@JoinColumn(name = "category_id", nullable = false)
	@ToString.Exclude
	private Category category;

	@Column(name = "amount", precision = 19, scale = 4, nullable = false)
	private BigDecimal amount;

	@Enumerated(EnumType.STRING)
	@Column(name = "transaction_type", nullable = false)
	private TransactionType transactionType;

	@Enumerated(EnumType.STRING)
	@Column(name = "recurrence_type", nullable = false)
	private RecurrenceType recurrenceType;

	/**
	 * Comma-separated month numbers (1-12) when this recurrence is active.
	 * Null for MONTHLY (fires every month).
	 * QUARTERLY defaults to "1,4,7,10", HALF_YEARLY to "1,7".
	 * YEARLY is a single month number. CUSTOM is user-defined.
	 */
	@Column(name = "active_months", length = 30)
	private String activeMonths;

	/** Expected day of month for the transaction (1-31). */
	@Column(name = "day_of_month", nullable = false)
	@Builder.Default
	private int dayOfMonth = 1;

	/** First month this obligation is active, format YYYY-MM. */
	@Column(name = "start_year_month", nullable = false, length = 7)
	private String startYearMonth;

	/** Last month this obligation is active (inclusive), format YYYY-MM. Null = indefinite. */
	@Column(name = "end_year_month", length = 7)
	private String endYearMonth;

	@Column(name = "enabled", nullable = false)
	@Builder.Default
	private boolean enabled = true;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;
}
