package com.nklmthr.finance.personal.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nklmthr.finance.personal.enums.PredictionType;

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
@Table(name = "prediction_rules", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"app_user_id", "category_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class PredictionRule {

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
	@JoinColumn(name = "category_id", nullable = false)
	@ToString.Exclude
	private Category category;

	@Enumerated(EnumType.STRING)
	@Column(name = "prediction_type", nullable = false)
	@Builder.Default
	private PredictionType predictionType = PredictionType.MONTHLY;

	@Column(name = "is_enabled", nullable = false)
	@Builder.Default
	private boolean enabled = true;

	@Column(name = "lookback_months", nullable = false)
	@Builder.Default
	private int lookbackMonths = 3;  // Default: look back 3 months for average

	@Column(name = "specific_month")
	private Integer specificMonth;  // For yearly predictions (1-12 for Jan-Dec)

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;
}

