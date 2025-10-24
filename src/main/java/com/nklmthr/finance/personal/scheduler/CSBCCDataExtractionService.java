package com.nklmthr.finance.personal.scheduler;

import java.math.BigDecimal;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.nklmthr.finance.personal.model.AccountTransaction;
import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.scheduler.util.PatternResult;
import com.nklmthr.finance.personal.scheduler.util.TransactionPatternLibrary;

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
		try {
			logger.debug("CSB CC extraction from: {}", emailContent);

			// Skip declined/failed transactions
			if (emailContent.toLowerCase().contains("has been declined") || 
			    emailContent.toLowerCase().contains("incorrect pin") ||
			    emailContent.toLowerCase().contains("transaction declined") ||
			    emailContent.toLowerCase().contains("transaction failed")) {
				logger.info("Skipping declined/failed transaction for CSB CC");
				return null;
			}

			// Use pattern library for extraction
			PatternResult<BigDecimal> amountResult = TransactionPatternLibrary.extractAmount(emailContent);
			if (amountResult.isPresent()) {
				tx.setAmount(amountResult.getValue());
				logger.debug("Extracted amount using pattern: {}", amountResult.getMatchedPattern());
			}

			PatternResult<String> descriptionResult = TransactionPatternLibrary.extractDescription(emailContent);
			if (descriptionResult.isPresent()) {
				tx.setDescription(descriptionResult.getValue());
				logger.debug("Extracted description using pattern: {}", descriptionResult.getMatchedPattern());
			}

			tx.setType(TransactionPatternLibrary.detectTransactionType(emailContent));
			tx.setAccount(accountService.getAccountByName("CSBB-CCA-OneCard", appUser));
			
			return tx;
		} catch (Exception e) {
			logger.error("Error parsing CSB CC transaction", e);
			return null;
		}
	}

}
