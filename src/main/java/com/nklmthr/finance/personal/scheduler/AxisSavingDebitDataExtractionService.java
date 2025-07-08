package com.nklmthr.finance.personal.scheduler;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
public class AxisSavingDebitDataExtractionService extends AbstractDataExtractionService {

	Logger logger = LoggerFactory.getLogger(AxisSavingDebitDataExtractionService.class);

	public static void main(String[] args) {
		AxisSavingDebitDataExtractionService service = new AxisSavingDebitDataExtractionService();
		service.run();
	}
	
    @Scheduled(cron = "0 0/3 * * * ?")  // Every 30 minutes
    public void runTask() {
        super.run();  // AbstractDataExtractionService logic
    }

	
	@Override
	protected List<String> getEmailSubject() {
		return Arrays.asList("Debit transaction alert for Axis Bank A/c", " was debited from your A/c no. XX2804.",
				"Notification from Axis Bank");
	}

	@Override
	protected String getSender() {
		return "alerts@axisbank.com";
	}

	
	@Override
	protected AccountTransaction extractTransactionData(String emailContent) {
		AccountTransaction accountTransaction = new AccountTransaction();

		// Extract Amount
		Matcher amountMatcher = Pattern.compile("Amount Debited: INR ([\\d,.]+)|INR ([\\d,.]+) has been debited")
				.matcher(emailContent);
		if (amountMatcher.find()) {
			String amountStr = amountMatcher.group(1) != null ? amountMatcher.group(1) : amountMatcher.group(2);
			accountTransaction.setAmount(new BigDecimal(amountStr.replace(",", "")));
		}

		// Extract Account (hardcoded, could be enhanced later)
		Matcher accMatcher = Pattern.compile("A/c no\\. ([XxXx\\d]+)").matcher(emailContent);
		if (accMatcher.find()) {
			//accountTransaction.setAccount(new Account("Axis Salary Acc"));
		}

		// Extract DateTime
		String pattern = "on (\\d{2}-\\d{2}-\\d{4})[ ,]*(\\d{2}:\\d{2}:\\d{2})|"
		               + "Date & Time: (\\d{2}-\\d{2}-\\d{2}), (\\d{2}:\\d{2}:\\d{2})";

		Pattern datePattern = Pattern.compile(pattern);
		Matcher matcher = datePattern.matcher(emailContent);

		DateTimeFormatter formatter1 = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
		DateTimeFormatter formatter2 = DateTimeFormatter.ofPattern("dd-MM-yy HH:mm:ss");

		if (matcher.find()) {
		    try {
		        if (matcher.group(1) != null && matcher.group(2) != null) {
		            accountTransaction.setDate(LocalDateTime.parse(
		                matcher.group(1) + " " + matcher.group(2), formatter1));
		        } else if (matcher.group(3) != null && matcher.group(4) != null) {
		            accountTransaction.setDate(LocalDateTime.parse(
		                matcher.group(3) + " " + matcher.group(4), formatter2));
		        } else {
		            logger.warn("Date groups matched but null values encountered.");
		        }
		    } catch (DateTimeParseException e) {
		        logger.error("Date parsing failed for Axis transaction email", e);
		    }
		} else {
		    logger.warn("No date pattern matched in email content");
		}

		// Extract Description
		if (accountTransaction.getDescription() == null) {
			String[] regexes = {
			        "Transaction Info:\\s*([A-Z0-9/\\- ]+)",  // UPI-style info
			        "INR [\\d,.]+ has been debited from your A/c no\\. .*? on .*? at ([A-Z0-9/\\-]+)", // ATM-WDL
			        "by ([A-Z0-9 \\-]+)",  // ACH
			        "at ([A-Z0-9/\\-]+)"   // fallback
			    };

			for (String regex : regexes) {
				String desc = extractTextUsingRegex(emailContent, regex, 1);
				if (desc != null && !desc.isBlank()) {
					accountTransaction.setDescription(desc.trim());
					break;
				}
			}
		}
		accountTransaction.setType(TransactionType.DEBIT);
		return accountTransaction;
	}

	public static String extractTextUsingRegex(String input, String patternStr, int group) {
		Pattern pattern = Pattern.compile(patternStr);
		Matcher matcher = pattern.matcher(input);
		if (matcher.find() && matcher.groupCount() == group) {
			return matcher.group(group);
		}
		return null;
	}
}
