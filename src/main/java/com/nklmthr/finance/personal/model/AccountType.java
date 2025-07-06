package com.nklmthr.finance.personal.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "account_types")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountType {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String name;

	private String description;

	private String classification;

	@Column(name = "account_type_balance", precision = 19, scale = 2)
	private BigDecimal accountTypeBalance;
}
