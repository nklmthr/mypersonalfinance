package com.nklmthr.finance.personal.scheduler;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

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
public class SBICCDataExtractionServiceImpl extends AbstractDataExtractionService {

	private static final Logger logger = LoggerFactory.getLogger(SBICCDataExtractionServiceImpl.class);

	@Value("${scheduler.enabled}")
	private boolean schedulerEnabled;

	@Scheduled(cron = "${my.scheduler.cron}")
	public void runTask() {
		if (!schedulerEnabled) {
			logger.info("Scheduler is disabled, skipping SBICCDataExtractionServiceImpl");
			return;
		}
		super.run();
	}

	@Override
	protected List<String> getEmailSubject() {
		return Arrays.asList("Transaction Alert from SBI Card");
	}

	@Override
	protected String getSender() {
		return "onlinesbicard@sbicard.com";
	}

	@Override
	protected AccountTransaction extractTransactionData(AccountTransaction tx, String emailContent, AppUser appUser) {
		try {
			// Skip declined/failed transactions
			if (emailContent.toLowerCase().contains("has been declined") || 
			    emailContent.toLowerCase().contains("incorrect pin") ||
			    emailContent.toLowerCase().contains("transaction declined") ||
			    emailContent.toLowerCase().contains("transaction failed")) {
				logger.info("Skipping declined/failed transaction for SBI CC");
				return null;
			}
			
			// Use pattern library for extraction
			PatternResult<BigDecimal> amountResult = TransactionPatternLibrary.extractAmount(emailContent);
			if (amountResult.isPresent()) {
				tx.setAmount(amountResult.getValue());
				logger.debug("Extracted amount using pattern: {}", amountResult.getMatchedPattern());
			} else {
				logger.warn("No amount match found in email");
			}

			PatternResult<String> descriptionResult = TransactionPatternLibrary.extractDescription(emailContent);
			if (descriptionResult.isPresent()) {
				tx.setDescription(descriptionResult.getValue());
				logger.debug("Extracted description using pattern: {}", descriptionResult.getMatchedPattern());
			}

			// Add UPI reference if present
			Optional<String> upiRef = TransactionPatternLibrary.extractUpiReference(emailContent);
			if (upiRef.isPresent() && tx.getDescription() != null) {
				tx.setDescription(tx.getDescription() + " UPI Ref " + upiRef.get());
			}

			// Account matching based on card number
			if (emailContent.contains("2606")) {
				tx.setAccount(accountService.getAccountByName("SBI Rupay Credit Card", appUser));
			} else {
				tx.setAccount(accountService.getAccountByName("SBIB-CCA-Signature", appUser));
			}

			tx.setType(TransactionPatternLibrary.detectTransactionType(emailContent));
		} catch (Exception e) {
			logger.error("Error parsing SBI Credit Card transaction", e);
		}
		return tx;
	}

}
