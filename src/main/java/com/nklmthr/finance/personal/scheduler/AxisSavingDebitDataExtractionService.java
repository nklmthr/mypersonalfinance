package com.nklmthr.finance.personal.scheduler;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.nklmthr.finance.personal.enums.TransactionType;
import com.nklmthr.finance.personal.model.AccountTransaction;
import com.nklmthr.finance.personal.service.AccountService;

@Service
public class AxisSavingDebitDataExtractionService extends AbstractDataExtractionService {

	private static final Logger logger = LoggerFactory.getLogger(AxisSavingDebitDataExtractionService.class);

	@Autowired
	private AccountService accountService;
	// ---- Patterns ----
	private static final Pattern AMOUNT_PATTERN = Pattern
			.compile("Amount Debited: INR ([\\d,]+(?:\\.\\d+)?)|INR ([\\d,]+(?:\\.\\d+)?) has been debited");

	private static final Pattern DATE_PATTERN = Pattern.compile("on (\\d{2}-\\d{2}-\\d{4})[, ]*(\\d{2}:\\d{2}:\\d{2})|"
			+ "Date & Time: (\\d{2}-\\d{2}-\\d{2}), (\\d{2}:\\d{2}:\\d{2})");

	private static final String[] DESCRIPTION_REGEXES = { "Transaction Info:\\s*([A-Z0-9/\\- ]+)",
			"INR [\\d,]+(?:\\.\\d+)? has been debited from your A/c no\\. .*? on .*? at ([A-Z0-9/\\-]+)",
			"by ([A-Z0-9 \\-]+)", "at ([A-Z0-9/\\-]+)" };

	private static final DateTimeFormatter FORMATTER_FULL = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
	private static final DateTimeFormatter FORMATTER_SHORT = DateTimeFormatter.ofPattern("dd-MM-yy HH:mm:ss");

	public static void main(String[] args) {
		AxisSavingDebitDataExtractionService service = new AxisSavingDebitDataExtractionService();
		service.run();
	}

	@Scheduled(cron = "0 0/60 * * * ?") // Every 30 minutes
	public void runTask() {
		super.run(); // AbstractDataExtractionService logic
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
		AccountTransaction tx = new AccountTransaction();

		extractAmount(emailContent).ifPresent(tx::setAmount);
		tx.setAccount(accountService.getAccountByName("Axis Salary Acc"));
		extractDateTime(emailContent).ifPresent(tx::setDate);
		extractDescription(emailContent).ifPresent(tx::setDescription);

		tx.setType(TransactionType.DEBIT);
		return tx;
	}

	// ---- Extraction helpers ----

	private static Optional<BigDecimal> extractAmount(String content) {
		Matcher m = AMOUNT_PATTERN.matcher(content);
		if (m.find()) {
			String raw = m.group(1) != null ? m.group(1) : m.group(2);
			try {
				return Optional.of(new BigDecimal(raw.replace(",", "")));
			} catch (NumberFormatException e) {
				logger.error("Failed to parse amount '{}'", raw, e);
			}
		}
		return Optional.empty();
	}

	private static Optional<LocalDateTime> extractDateTime(String content) {
		Matcher m = DATE_PATTERN.matcher(content);
		if (m.find()) {
			try {
				if (m.group(1) != null && m.group(2) != null) {
					return Optional.of(LocalDateTime.parse(m.group(1) + " " + m.group(2), FORMATTER_FULL));
				} else if (m.group(3) != null && m.group(4) != null) {
					return Optional.of(LocalDateTime.parse(m.group(3) + " " + m.group(4), FORMATTER_SHORT));
				}
			} catch (DateTimeParseException e) {
				logger.error("Failed to parse date/time from '{}'", m.group(), e);
			}
		} else {
			logger.warn("No date/time found in email");
		}
		return Optional.empty();
	}

	private static Optional<String> extractDescription(String content) {
		for (String regex : DESCRIPTION_REGEXES) {
			Optional<String> match = extractGroup(Pattern.compile(regex), content, 1);
			if (match.isPresent() && !match.get().isBlank()) {
				return match;
			}
		}
		return Optional.empty();
	}

	/**
	 * Utility: run `pattern` against `input` and return group `groupIndex` if found
	 * & non‑empty.
	 */
	private static Optional<String> extractGroup(Pattern pattern, String input, int groupIndex) {
		Matcher m = pattern.matcher(input);
		if (m.find()) {
			String g = m.group(groupIndex);
			if (g != null && !g.isBlank()) {
				return Optional.of(g.trim());
			}
		}
		return Optional.empty();
	}

}
