package com.nklmthr.finance.personal.scheduler;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.nklmthr.finance.personal.enums.TransactionType;
import com.nklmthr.finance.personal.model.AccountTransaction;
import com.nklmthr.finance.personal.model.AppUser;

@Service
public class SBICCDataExtractionServiceImpl extends AbstractDataExtractionService {

	private static final Logger logger = LoggerFactory.getLogger(SBICCDataExtractionServiceImpl.class);

	// Slash-based fallback pattern
	private static final Pattern GENERIC_PATTERN = Pattern.compile(
			"Rs\\.([\\d\\.]+) spent on your SBI Credit Card ending .*? at (.*?) on (\\d{2}[/-]\\d{2}[/-]\\d{2})",
			Pattern.CASE_INSENSITIVE);

	private static final Pattern REF_PATTERN = Pattern.compile("Ref No\\.\\s*(\\d+)");

	@Scheduled(cron = "0 0/5 * * * ?")
	public void runTask() {
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
			Matcher m = GENERIC_PATTERN.matcher(emailContent);
			if (m.find()) {
				// Amount
				tx.setAmount(new BigDecimal(m.group(1)));

				// Merchant
				tx.setDescription(m.group(2).trim());

			} else {
				logger.warn("No SBI Credit Card match found in: {}", emailContent);
			}

			// UPI Ref
			Matcher ref = REF_PATTERN.matcher(emailContent);
			if (ref.find()) {
				tx.setDescription(tx.getDescription()+" UPI Ref " + ref.group(1));
			}

			// Account selection
			if (emailContent.contains("2606")) {
				tx.setAccount(accountService.getAccountByName("SBI Rupay Credit Card", appUser));
			} else {
				tx.setAccount(accountService.getAccountByName("SBIB-CCA-Signature", appUser));
			}

			tx.setType(TransactionType.DEBIT);
		} catch (Exception e) {
			logger.error("Error parsing SBI Credit Card transaction", e);
		}
		return tx;
	}

}
