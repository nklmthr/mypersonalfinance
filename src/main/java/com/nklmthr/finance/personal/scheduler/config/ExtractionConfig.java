package com.nklmthr.finance.personal.scheduler.config;

import java.util.List;

import com.nklmthr.finance.personal.enums.TransactionType;

/**
 * Configuration for email-based transaction extraction.
 * Each instance represents a specific bank/account type combination.
 */
public class ExtractionConfig {
	private String name;
	private List<String> emailSubjects;
	private String sender;
	private TransactionType fixedTransactionType; // null means auto-detect
	private boolean skipDeclinedTransactions;

	public ExtractionConfig(String name, List<String> emailSubjects, String sender, 
	                        TransactionType fixedTransactionType, boolean skipDeclinedTransactions) {
		this.name = name;
		this.emailSubjects = emailSubjects;
		this.sender = sender;
		this.fixedTransactionType = fixedTransactionType;
		this.skipDeclinedTransactions = skipDeclinedTransactions;
	}

	public String getName() {
		return name;
	}

	public List<String> getEmailSubjects() {
		return emailSubjects;
	}

	public String getSender() {
		return sender;
	}

	public TransactionType getFixedTransactionType() {
		return fixedTransactionType;
	}

	public boolean isSkipDeclinedTransactions() {
		return skipDeclinedTransactions;
	}

	public boolean hasFixedTransactionType() {
		return fixedTransactionType != null;
	}
}

