package com.nklmthr.finance.personal.scheduler;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.nklmthr.finance.personal.enums.TransactionType;
import com.nklmthr.finance.personal.model.AccountTransaction;
import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.scheduler.util.PatternResult;
import com.nklmthr.finance.personal.scheduler.util.TransactionPatternLibrary;

@Service
public class AmazonRefundOnOrderExtraction extends AbstractDataExtractionService {

	private static final Logger logger = LoggerFactory.getLogger(AmazonRefundOnOrderExtraction.class);

	@Value("${scheduler.enabled}")
	private boolean schedulerEnabled;

	@Scheduled(cron = "${my.scheduler.cron}")
	public void runTask() {
		if (!schedulerEnabled) {
			logger.info("Scheduler is disabled, skipping AmazonRefundOnOrderExtraction");
			return;
		}
		super.run();
	}

	@Override
	protected List<String> getEmailSubject() {
		return List.of("Refund on order ");
	}

	@Override
	protected String getSender() {
		return "payments-messages@amazon.in";
	}

	@Override
	protected AccountTransaction extractTransactionData(AccountTransaction accountTransaction, String emailContent,
			AppUser appUser) {
		try {
			logger.debug("Extracting Amazon refund transaction");

			// Use pattern library for amount extraction
			PatternResult<BigDecimal> amountResult = TransactionPatternLibrary.extractAmount(emailContent);
			if (amountResult.isPresent()) {
				accountTransaction.setAmount(amountResult.getValue());
				logger.debug("Extracted amount using pattern: {}", amountResult.getMatchedPattern());
			}

			// Extract Order ID for description
			Pattern orderIdPattern = Pattern.compile("Order #\\s*([\\d-]+)");
			Matcher orderMatcher = orderIdPattern.matcher(emailContent);
			if (orderMatcher.find()) {
				accountTransaction.setDescription(orderMatcher.group(1));
			}

			// Extract Item Name
			Pattern itemPattern = Pattern.compile("Item:\\s*(.*?)Quantity:", Pattern.DOTALL);
			Matcher itemMatcher = itemPattern.matcher(emailContent);
			if (itemMatcher.find()) {
				accountTransaction.setExplanation("Refund for: " + itemMatcher.group(1).trim());
			}

			// Extract Quantity
			Pattern qtyPattern = Pattern.compile("Quantity:\\s*(\\d+)");
			Matcher qtyMatcher = qtyPattern.matcher(emailContent);
			if (qtyMatcher.find()) {
				accountTransaction.setExplanation(accountTransaction.getExplanation() + " | Quantity: " + qtyMatcher.group(1));
			}

			// Extract Refund Mode and set account
			Pattern refundModePattern = Pattern.compile("credited as follows:\\s*(.*?)\\s*:", Pattern.DOTALL);
			Matcher modeMatcher = refundModePattern.matcher(emailContent);
			if (modeMatcher.find()) {
				String mode = modeMatcher.group(1).trim();
				logger.debug("Detected refund mode: {}", mode);
				if (mode.toLowerCase().contains("credit card") && mode.contains("9057")) {
					accountTransaction.setAccount(accountService.getAccountByName("ICICI-CCA-Amazon", appUser));
				} else if (mode.toLowerCase().contains("amazon pay") || mode.equalsIgnoreCase("GC")) {
					accountTransaction.setAccount(accountService.getAccountByName("AMZN-WLT-Amazon Pay", appUser));
				}
			}

			accountTransaction.setType(TransactionType.CREDIT);
			return accountTransaction;
		} catch (Exception e) {
			logger.error("Failed to extract Amazon refund transaction", e);
			return accountTransaction;
		}
	}

}
