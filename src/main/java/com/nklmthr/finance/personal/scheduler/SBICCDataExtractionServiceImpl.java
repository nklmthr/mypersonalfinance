package com.nklmthr.finance.personal.scheduler;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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

@Service
public class SBICCDataExtractionServiceImpl extends AbstractDataExtractionService {
	private static final Logger logger = LoggerFactory.getLogger(SBICCDataExtractionServiceImpl.class);

	private static final Pattern SBI_CREDIT_REGEX = Pattern.compile(
			"Rs\\.([\\d\\.]+) spent on your SBI Credit Card ending with \\d+ at (.+?) on (\\d{2}-\\d{2}-\\d{2}) via (UPI) \\(Ref No\\. ([\\d]+)\\)");

	public static void main(String[] args) {
		SBICCDataExtractionServiceImpl impl = new SBICCDataExtractionServiceImpl();
		impl.run();
	}

	@Scheduled(cron = "0 0/3 * * * ?") // Every 30 minutes
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
	protected AccountTransaction extractTransactionData(AccountTransaction tx, String emailContent) {
		try {
			// Extract amount
			Pattern amountPattern = Pattern.compile("Rs\\.(\\d+(?:\\.\\d{1,2})?)");
			Matcher amountMatcher = amountPattern.matcher(emailContent);
			if (amountMatcher.find()) {
				tx.setAmount(new BigDecimal(amountMatcher.group(1)));
			}

			// Extract merchant name
			Pattern merchantPattern = Pattern.compile("at (.*?) on \\d{2}-\\d{2}-\\d{2}");
			Matcher merchantMatcher = merchantPattern.matcher(emailContent);
			if (merchantMatcher.find()) {
				tx.setDescription(merchantMatcher.group(1).trim());
			}

			// Extract reference number (optional explanation)
			Pattern refPattern = Pattern.compile("Ref No\\.\\s*(\\d+)");
			Matcher refMatcher = refPattern.matcher(emailContent);
			if (refMatcher.find()) {
				tx.setExplanation("UPI Ref " + refMatcher.group(1));
			}

			tx.setDate(tx.getSourceTime());

			tx.setType(TransactionType.DEBIT);
			tx.setAccount(emailContent.contains("2606") ? accountService.getAccountByName("SBI Rupay Credit Card")
					: accountService.getAccountByName("SBIB-CCA-Signature"));
		} catch (Exception e) {
			logger.error("Error parsing SBI Credit Card transaction", e);
		}
		return tx;
	}
}
