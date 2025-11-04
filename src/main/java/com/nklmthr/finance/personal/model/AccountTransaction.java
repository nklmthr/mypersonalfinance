package com.nklmthr.finance.personal.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.annotations.UuidGenerator;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nklmthr.finance.personal.enums.TransactionType;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
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
	@JoinColumn(name = "app_user_id", nullable = false)
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

	@OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	@ToString.Exclude
	@Builder.Default
	private List<TransactionLabel> transactionLabels = new ArrayList<>();

	// Helper methods to work with labels
	public List<Label> getLabels() {
		if (transactionLabels == null) {
			return new ArrayList<>();
		}
		return transactionLabels.stream()
			.map(TransactionLabel::getLabel)
			.toList();
	}

	public void addLabel(Label label, AppUser appUser) {
		if (transactionLabels == null) {
			transactionLabels = new ArrayList<>();
		}
		TransactionLabel transactionLabel = TransactionLabel.builder()
			.transaction(this)
			.label(label)
			.appUser(appUser)
			.build();
		transactionLabels.add(transactionLabel);
	}

	public void removeLabel(Label label) {
		if (transactionLabels != null) {
			transactionLabels.removeIf(tl -> tl.getLabel().getId().equals(label.getId()));
		}
	}

	public void setLabels(List<Label> labels, AppUser appUser) {
		if (transactionLabels == null) {
			transactionLabels = new ArrayList<>();
		}
		
		// Get the current label IDs
		Set<String> currentLabelIds = transactionLabels.stream()
			.map(tl -> tl.getLabel().getId())
			.collect(java.util.stream.Collectors.toSet());
		
		// Get the new label IDs
		Set<String> newLabelIds = (labels != null) 
			? labels.stream().map(Label::getId).collect(java.util.stream.Collectors.toSet())
			: new HashSet<>();
		
		// Remove labels that are no longer present
		transactionLabels.removeIf(tl -> !newLabelIds.contains(tl.getLabel().getId()));
		
		// Add new labels that aren't already present
		if (labels != null) {
			labels.stream()
				.filter(label -> !currentLabelIds.contains(label.getId()))
				.forEach(label -> addLabel(label, appUser));
		}
	}

}
