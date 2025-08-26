package com.nklmthr.finance.personal.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.UuidGenerator;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nklmthr.finance.personal.enums.TransactionType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "account_transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountTransaction {

	@Id
	@UuidGenerator
	@Column
	private String id;

	private LocalDateTime date;

	private BigDecimal amount;

	@Column(nullable = false, length = 1000)
	private String description;

	@Column(length = 2000)
	private String explanation;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TransactionType type;

	@Column
	private String href;

	@Column
	private String hrefText;

	@Column
	@JsonIgnore
	private String sourceId;

	@Column
	@JsonIgnore
	private String sourceThreadId;

	@Column
	@JsonIgnore
	private LocalDateTime sourceTime;

	@ManyToOne
	private Account account;

	@ManyToOne
	@JoinColumn(name = "category_id")
	private Category category;

	@Column(name= "parent_id")
	private String parent;

	@ManyToOne(optional = false)
	@JsonIgnore
	private AppUser appUser;

	@ManyToOne
	@JoinColumn(name = "uploaded_statement_id")
	@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
	private UploadedStatement uploadedStatement;

//	@OneToMany(mappedBy = "accountTransaction", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
//	@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
//	@Builder.Default
//	private List<Attachment> attachments = new ArrayList<>();

	@Column
	private String dataVersionId;

}
