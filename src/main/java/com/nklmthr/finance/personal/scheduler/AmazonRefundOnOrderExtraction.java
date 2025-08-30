package com.nklmthr.finance.personal.scheduler;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
		logger.debug("Extracting transaction data from email content: {}", emailContent);
		Pattern orderIdPattern = Pattern.compile("Order #\\s*([\\d-]+)");
		Pattern amountPattern = Pattern.compile("refund for Rs\\.([\\d,.]+)");
		Pattern itemPattern = Pattern.compile("Item:\\s*(.*?)Quantity:", Pattern.DOTALL);
		Pattern qtyPattern = Pattern.compile("Quantity:\\s*(\\d+)");
		Pattern refundModePattern = Pattern.compile("credited as follows:\\s*(.*?)\\s*:", Pattern.DOTALL);
		Matcher m;

		// Order ID
		m = orderIdPattern.matcher(emailContent);
		if (m.find()) {
			accountTransaction.setDescription(m.group(1));
		}

		// Refund Amount
		m = amountPattern.matcher(emailContent);
		if (m.find()) {
			String amt = m.group(1).replace(",", "");
			accountTransaction.setAmount(new BigDecimal(amt));
		}

		// Item Name
		m = itemPattern.matcher(emailContent);
		if (m.find()) {
			accountTransaction.setExplanation("Refund for: " + m.group(1).trim());
		}

		// Quantity
		m = qtyPattern.matcher(emailContent);
		if (m.find()) {
			accountTransaction.setExplanation(accountTransaction.getExplanation() + " | Quantity: " + m.group(1));
		}

		// Refund Mode
		m = refundModePattern.matcher(emailContent);
		if (m.find()) {
			String mode = m.group(1).trim();
			logger.debug("Detected refund mode: {}", mode);
			if (mode.toLowerCase().contains("credit card") && mode.contains("9057")) {
				accountTransaction.setAccount(accountService.getAccountByName("ICICI-CCA-Amazon", appUser));
			} else if (mode.toLowerCase().contains("amazon pay") || mode.equalsIgnoreCase("GC")) {
				accountTransaction.setAccount(accountService.getAccountByName("AMZN-WLT-Amazon Pay", appUser));
			}
		}
		accountTransaction.setType(TransactionType.CREDIT);
		return accountTransaction;
	}

}
