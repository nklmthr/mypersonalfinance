package com.nklmthr.finance.personal.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.UuidGenerator;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "account_balance_snapshot")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountBalanceSnapshot {

	@Id
	@UuidGenerator
	@Column
	private String id;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "account_id", nullable = false)
	private Account account;

	@Column(nullable = false)
	private LocalDateTime snapshotDate;

	@Column(nullable = false)
	private BigDecimal balance;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JsonIgnore
	private AppUser appUser;

}
