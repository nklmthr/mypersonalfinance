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

@Service
public class CSBCCDataExtractionService extends AbstractDataExtractionService {

	private static final Logger logger = LoggerFactory.getLogger(CSBCCDataExtractionService.class);

	@Value("${scheduler.enabled}")
	private boolean schedulerEnabled;

	@Scheduled(cron = "${my.scheduler.cron}")
	public void runTask() {
		if (!schedulerEnabled) {
			logger.info("Scheduler is disabled, skipping CSBCCDataExtractionService");
			return;
		}
		super.run();
	}

	public static void main(String[] args) {
		CSBCCDataExtractionService service = new CSBCCDataExtractionService();
		service.run();
	}

	@Override
	protected List<String> getEmailSubject() {
		return List.of("Payment update on your CSB One credit card");
	}

	@Override
	protected String getSender() {
		return "no-reply@getonecard.app";
	}

	protected AccountTransaction extractTransactionData(AccountTransaction tx, String emailContent, AppUser appUser) {
		logger.info("Content: {}", emailContent);
		String regex = "Credit Card ending in (\\d{4}).*?Amount: INR ([\\d,]+\\.\\d{2})\\s+Merchant: (.*?)\\s+Date: (\\d{2}/\\d{2}/\\d{4})\\s+Time: (\\d{2}:\\d{2}:\\d{2})";

		// Compile and match
		Pattern pattern = Pattern.compile(regex, Pattern.DOTALL);
		Matcher matcher = pattern.matcher(emailContent);

		if (matcher.find()) {
			String cardEnding = matcher.group(1);
			String amount = matcher.group(2);
			String merchant = matcher.group(3);
			tx.setAmount(new BigDecimal(amount.replace(",", "")));
			tx.setDescription(merchant);
			tx.setType(TransactionType.DEBIT);
			tx.setAccount(accountService.getAccountByName("CSBB-CCA-OneCard", appUser));
			logger.info("Extracted - Card Ending: {}, Amount: {}, Merchant: {}", cardEnding, amount, merchant);
			return tx;
		}
		return null;
	}

}
