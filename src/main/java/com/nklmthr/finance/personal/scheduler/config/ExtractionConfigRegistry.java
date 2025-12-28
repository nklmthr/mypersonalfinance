package com.nklmthr.finance.personal.scheduler.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;

import com.nklmthr.finance.personal.enums.TransactionType;

/**
 * Central registry of all transaction extraction configurations.
 * Add new bank/account configurations here instead of creating new service classes.
 */
@Component
public class ExtractionConfigRegistry {

	private final List<ExtractionConfig> configs;

	public ExtractionConfigRegistry() {
		configs = new ArrayList<>();
		
		// Axis Bank Credit Card
		configs.add(new ExtractionConfig(
			"AxisCC",
			List.of(
				"Transaction alert on Axis Bank Credit Card no. XX0434",
				"spent on credit card no. XX0434",
				"Transaction alert on Axis Bank Credit Card no. XX7002",
				"Axis Bank Credit Card Transaction Alert"
			),
			"alerts@axis.bank.in",
			null, // auto-detect transaction type
			true  // skip declined transactions
		));

		// Axis Bank Savings - handles both debit and credit
		configs.add(new ExtractionConfig(
			"AxisSaving",
			Arrays.asList(
				"Debit transaction alert for Axis Bank A/c",
				"Credit transaction alert for Axis Bank A/c",
				"was debited from your A/c no. XX2804",
				"was credited to your A/c",
				"has been credited with INR",
				"has been debited with INR",
				"Notification from Axis Bank"
			),
			"alerts@axisbank.com",
			null, // auto-detect transaction type (DEBIT or CREDIT)
			true	 // skip declined transactions
		));

		// CSB Bank Credit Card
		configs.add(new ExtractionConfig(
			"CSBCC",
			List.of("Payment update on your CSB One credit card"),
			"no-reply@getonecard.app",
			null, // auto-detect transaction type
			true  // skip declined transactions
		));

		// Yes Bank Credit Card
		configs.add(new ExtractionConfig(
			"YesBankCC",
			List.of("YES BANK - Transaction Alert"),
			"alerts@yesbank.in",
			null, // auto-detect transaction type
			true  // skip declined transactions
		));

		// ICICI Bank Credit Card
		configs.add(new ExtractionConfig(
			"ICICICC",
			List.of("Transaction alert for your ICICI Bank Credit Card"),
			"credit_cards@icicibank.com",
			null, // auto-detect transaction type
			true  // skip declined transactions
		));

		// SBI Bank Credit Card
		configs.add(new ExtractionConfig(
			"SBICC",
			List.of("Transaction Alert from SBI Card"),
			"onlinesbicard@sbicard.com",
			null, // auto-detect transaction type
			true  // skip declined transactions
		));

		// Amazon Pay Monthly Reward
		configs.add(new ExtractionConfig(
			"AmazonPayMonthlyReward",
			List.of("Your monthly reward points for using Amazon Pay ICICI Bank credit card added to your Amazon Pay balance"),
			"no-reply@amazonpay.in",
			TransactionType.CREDIT, // always credit
			false
		));

		// Amazon Pay Refund
		configs.add(new ExtractionConfig(
			"AmazonPayRefund",
			List.of(
				"Update on refund processed for your order",
				"Refund for your Amazon Pay transaction"
			),
			"no-reply@amazonpay.in",
			TransactionType.CREDIT, // refunds are credits
			false
		));
	}

	public List<ExtractionConfig> getAllConfigs() {
		return configs;
	}
}

