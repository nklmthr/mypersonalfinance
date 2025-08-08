package com.nklmthr.finance.personal.scheduler;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.nklmthr.finance.personal.enums.TransactionType;
import com.nklmthr.finance.personal.model.AccountTransaction;
import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.service.AccountService;

@Service
public class AxisSavingCreditDataExtractionService extends AbstractDataExtractionService {

	private static final Logger logger = LoggerFactory.getLogger(AxisSavingCreditDataExtractionService.class);

	@Autowired
	private AccountService accountService;

	@Value("${scheduler.enabled}")
	private boolean schedulerEnabled;

	@Scheduled(cron = "${my.scheduler.cron}")
	public void runTask() {
		if (!schedulerEnabled) {
			logger.info("Scheduler is disabled, skipping Axis CC data extraction");
			return;
		}
		super.run();
	}

	public static void main(String[] args) {
		AxisSavingCreditDataExtractionService service = new AxisSavingCreditDataExtractionService();
		service.run();
	}

	@Override
	protected List<String> getEmailSubject() {
		return Arrays.asList("Credit transaction alert for Axis Bank A/c", "was credited to your A/c.",
				"Credit notification from Axis Bank");
	}

	@Override
	protected String getSender() {
		return "alerts@axisbank.com";
	}

	@Override
	protected AccountTransaction extractTransactionData(AccountTransaction tx, String emailContent, AppUser appUser) {
		try {
			logger.debug("Parsing Axis Bank credit transaction email content:\n" + emailContent);
			tx.setType(TransactionType.CREDIT);
			tx.setAccount(accountService.getAccountByName("Axis Salary Acc", appUser));

			Map<String, BigDecimal> valuesFromPattern = null;

			// Pattern 1: Reference No. format
			valuesFromPattern = getTransactionDetails(emailContent,
					"INR ([\\d,]+\\.\\d{2}) has been credited to .*? on (\\d{2}-\\d{2}-\\d{2}) at ([\\d:]+) IST\\. Reference no\\. - ([^\\.\\n]+)",
					4);

			// Pattern 2: 'Amount Credited: ... Transaction Info:'
			if (valuesFromPattern == null) {
				valuesFromPattern = getTransactionDetails(emailContent,
						"Amount Credited: INR ([\\d,]+\\.\\d{2})\\s+" + "Account Number: (\\S+)\\s+"
								+ "Date & Time: (\\d{2}-\\d{2}-\\d{2}), ([\\d:]+) IST\\s+"
								+ "Transaction Info: ([^\\r\\n]+)",
						4);
			}

			// Pattern 3: 'has been credited with ... by ...'
			if (valuesFromPattern == null) {
				valuesFromPattern = getTransactionDetails(emailContent,
						"has been credited with INR ([\\d,]+\\.\\d{2}) on (\\d{2}-\\d{2}-\\d{4}) at ([\\d:]+) IST by ([^\\.\\n]+)",
						4);
			}
			if (valuesFromPattern == null) {
				valuesFromPattern = getTransactionDetails(emailContent,
						"Amount Credited: INR ([\\d,]+\\.\\d{2}) Account Number: (\\S+) Date & Time: (\\d{2}-\\d{2}-\\d{2}), ([\\d:]+) IST Transaction Info: ([^\\r\\n]+)",
						5);
			}
			// Pattern 4: Alternate fallback - short format
			if (valuesFromPattern == null) {
				valuesFromPattern = getTransactionDetails(emailContent,
						"your A/c no\\. .*? has been credited with INR ([\\d,]+\\.\\d{2}) on (\\d{2}-\\d{2}-\\d{4}) at ([\\d:]+) IST by ([^\\.\\n]+)",
						4);
			}

			// Pattern 5: 'Amount = ... Description = ...'
			if (valuesFromPattern == null) {
				valuesFromPattern = getTransactionDetails(emailContent,
						"Amount = ([\\d,]+\\.\\d{2})[^\\r\\n]+Description = ([^\r\n]+)", 2);
			}

			// Handle matched values
			if (valuesFromPattern != null && !valuesFromPattern.isEmpty()) {
				Map.Entry<String, BigDecimal> entry = valuesFromPattern.entrySet().iterator().next();
				tx.setDescription(entry.getKey());
				tx.setAmount(entry.getValue());
			} else {
				throw new IllegalArgumentException("No transaction data found in email content");
			}

		} catch (Exception e) {
			logger.error("Failed to parse Axis credit transaction", e);
		}

		return tx;
	}

	private Map<String, BigDecimal> getTransactionDetails(String emailContent, String pattern,
			int descriptionGroupIndex) {
		Pattern regex = Pattern.compile(pattern);
		Matcher matcher = regex.matcher(emailContent);
		if (matcher.find()) {
			String amountStr = matcher.group(1).replaceAll(",", "");
			BigDecimal amount = new BigDecimal(amountStr);

			String rawDescription = matcher.group(descriptionGroupIndex);
			String description = rawDescription.split("[\\r\\n]")[0].trim();

			// Truncate at known footers or contact messages
			for (String stopWord : List.of("Regards,", "Call us at", "Always open", "***", "Reach us at",
					"For any concerns")) {
				int index = description.indexOf(stopWord);
				if (index != -1) {
					description = description.substring(0, index).trim();
					break;
				}
			}

			logger.debug("Extracted transaction description: {}", description);
			return Map.of(description, amount);
		}

		logger.debug("No match found in {} for pattern: {}", emailContent, pattern);
		return null;
	}
}
