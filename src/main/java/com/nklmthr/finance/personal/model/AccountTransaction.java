package com.nklmthr.finance.personal.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.UuidGenerator;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nklmthr.finance.personal.enums.TransactionType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "account_transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
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
	@JoinColumn(name = "account_id", nullable = false)
	@ToString.Exclude
	private Account account;

	@ManyToOne
	@JoinColumn(name = "category_id")
	@ToString.Exclude
	private Category category;

	@Column(name= "parent_id")
	private String parent;
	
	@Column(name = "linked_transfer_id")
	private String linkedTransferId;

	@ManyToOne(optional = false)
	@JsonIgnore
	@ToString.Exclude
	private AppUser appUser;

	@ManyToOne
	@JoinColumn(name = "uploaded_statement_id")
	@ToString.Exclude
	private UploadedStatement uploadedStatement;

//	@OneToMany(mappedBy = "accountTransaction", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
//	@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
//	@Builder.Default
//	private List<Attachment> attachments = new ArrayList<>();

	@Column
	private String dataVersionId;
	
	@Column
	private String currency;
	
	@Lob
    @Column(columnDefinition = "MEDIUMTEXT")
	@ToString.Exclude
	private String rawData;
	
	@Column(precision = 19, scale = 4)
	private BigDecimal gptAmount;

	@Column(length = 1000)
	private String gptDescription;

	@Column(length = 2000)
	private String gptExplanation;

	@Enumerated(EnumType.STRING)
	@Column(length = 50)
	private TransactionType gptType;

	@ManyToOne
	@JoinColumn(name = "gpt_account_id", nullable = false)
	@ToString.Exclude
	private Account gptAccount;
	
	@Column
	private String gptCurrency;

}
