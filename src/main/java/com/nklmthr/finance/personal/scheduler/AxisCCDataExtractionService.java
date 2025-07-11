package com.nklmthr.finance.personal.scheduler;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
public class AxisCCDataExtractionService extends AbstractDataExtractionService {

	private static final Logger logger = LoggerFactory.getLogger(AxisCCDataExtractionService.class);

	@Scheduled(cron = "0 0/3 * * * ?") // Every 30 minutes
	public void runTask() {
		super.run();
	}

	public static void main(String[] args) {
		AxisCCDataExtractionService service = new AxisCCDataExtractionService();
		service.run();
	}

	@Override
	protected List<String> getEmailSubject() {
		return List.of("Transaction alert on Axis Bank Credit Card no. XX0434",
				"Transaction alert on Axis Bank Credit Card no. XX7002", "Axis Bank Credit Card Transaction Alert");
	}

	@Override
	protected String getSender() {
		return "alerts@axisbank.com";
	}

	public AccountTransaction extractTransactionData(AccountTransaction tx, String emailContent) {
		try {
			// Amount (e.g., "INR 5090" or "INR 5,090.00")
			Pattern amountPattern = Pattern.compile("for INR ([\\d,]+\\.?\\d*)");
			Matcher amountMatcher = amountPattern.matcher(emailContent);
			if (amountMatcher.find()) {
				String amountStr = amountMatcher.group(1).replace(",", "");
				tx.setAmount(new BigDecimal(amountStr));
			}

			// Date and time (e.g., "on 08-07-2025 19:15:07 IST")
			Pattern dateTimePattern = Pattern.compile("on (\\d{2}-\\d{2}-\\d{4}) (\\d{2}:\\d{2}:\\d{2})");
			Matcher dateTimeMatcher = dateTimePattern.matcher(emailContent);
			if (dateTimeMatcher.find()) {
				String dateStr = dateTimeMatcher.group(1);
				String timeStr = dateTimeMatcher.group(2);
				DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
				tx.setDate(LocalDateTime.parse(dateStr + " " + timeStr, formatter));
			}

			// Merchant name (e.g., "at Kharva Ente", "at SUBHA MEDIC")
			Pattern merchantPattern = Pattern.compile("at ([A-Za-z0-9 &\\-]+?) on \\d{2}-\\d{2}-\\d{4}");
			Matcher merchantMatcher = merchantPattern.matcher(emailContent);
			if (merchantMatcher.find()) {
				String merchant = merchantMatcher.group(1).trim();
				tx.setDescription(merchant);
			}

			// Explanation: set to fixed value or parsed info (optional)
			Pattern refPattern = Pattern.compile("E\\d{9}_\\d{2}_\\d{2}"); // like E002523120_06_24
			Matcher refMatcher = refPattern.matcher(emailContent);
			if (refMatcher.find()) {
				tx.setExplanation("Axis Credit Card Transaction Ref: " + refMatcher.group());
			}

			tx.setType(TransactionType.DEBIT);
			tx.setAccount(emailContent.contains("0434") ? accountService.getAccountByName("AXIS-CCA-Airtel")
					: accountService.getAccountByName("Axis-Citi-PremierMiles"));

		} catch (Exception e) {
			logger.error("Error parsing Axis CC transaction", e);
		}

		return tx;
	}

}
