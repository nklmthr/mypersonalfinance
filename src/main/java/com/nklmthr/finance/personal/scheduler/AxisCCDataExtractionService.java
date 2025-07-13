package com.nklmthr.finance.personal.scheduler;

import java.math.BigDecimal;
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
public class AxisCCDataExtractionService extends AbstractDataExtractionService {

	private static final Logger logger = LoggerFactory.getLogger(AxisCCDataExtractionService.class);

	@Scheduled(cron = "0 0/5 * * * ?") // Every 30 minutes
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

	public AccountTransaction extractTransactionData(AccountTransaction tx, String emailContent, AppUser appUser) {
		try {
			// Amount (e.g., "INR 5090" or "INR 5,090.00")
			Pattern amountPattern = Pattern.compile("for INR ([\\d,]+\\.?\\d*)");
			Matcher amountMatcher = amountPattern.matcher(emailContent);
			if (amountMatcher.find()) {
				String amountStr = amountMatcher.group(1).replace(",", "");
				tx.setAmount(new BigDecimal(amountStr));
			}

			// Merchant name (e.g., "at Kharva Ente", "at SUBHA MEDIC")
			Pattern merchantPattern = Pattern.compile("at ([A-Za-z0-9 &\\-]+?) on \\d{2}-\\d{2}-\\d{4}");
			Matcher merchantMatcher = merchantPattern.matcher(emailContent);
			if (merchantMatcher.find()) {
				String merchant = merchantMatcher.group(1).trim();
				tx.setDescription(merchant);
			}

			Pattern refPattern = Pattern.compile("E\\d{9}_\\d{2}_\\d{2}"); // like E002523120_06_24
			Matcher refMatcher = refPattern.matcher(emailContent);
			if (refMatcher.find()) {
				tx.setDescription(tx.getDescription() +"Transaction Ref: " + refMatcher.group());
			}

			tx.setType(TransactionType.DEBIT);
			tx.setAccount(emailContent.contains("0434") ? accountService.getAccountByName("AXIS-CCA-Airtel", appUser)
					: accountService.getAccountByName("Axis-Citi-PremierMiles", appUser));

		} catch (Exception e) {
			logger.error("Error parsing Axis CC transaction", e);
		}

		return tx;
	}

}
