package com.nklmthr.finance.personal.upload.parser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.nklmthr.finance.personal.enums.TransactionType;
import com.nklmthr.finance.personal.model.AccountTransaction;
import com.nklmthr.finance.personal.model.UploadedStatement;

public class SBICsvParser extends StatementParser {
	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SBICsvParser.class);
	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy");

	@Override
	protected List<AccountTransaction> mapTransactions(List<String[]> rows, UploadedStatement statement) {
		List<AccountTransaction> transactions = new ArrayList<>();

		for (String[] row : rows) {
			if (row[0].isBlank()) {
				continue;
			}
	
			LocalDateTime date = null;
			try {
				if (row[0].length() < 11) {
					logger.warn("Invalid date format in row: {}", row[0]);
					continue; // skip invalid date rows
				}
				LocalDate datePart = LocalDate.parse(row[0].trim(), DATE_FORMATTER);
				date = datePart.atStartOfDay();
			} catch (Exception e) {
				logger.error("Error parsing date from row: {}", row[0], e);
				continue; // skip rows with date parsing errors
			}
			

			String transSplit[] = splitTransferDescription(row[1].trim());
			if(transSplit[0] == null || transSplit[0].isBlank()) {
				logger.warn("Transaction with empty description: {}", row[1]);
			}
			String description = transSplit[0];
			String explanation = transSplit[1] != null ? transSplit[1] : "";
			String debitStr = row[4].replace(",", "").trim();
			String creditStr = row[5].replace(",", "").trim();

			BigDecimal amount;
			boolean isCredit;

			if (!debitStr.isEmpty() && !debitStr.equals("-")) {
				amount = new BigDecimal(debitStr);
				isCredit = false;
			} else if (!creditStr.isEmpty() && !creditStr.equals("-")) {
				amount = new BigDecimal(creditStr);
				isCredit = true;
			} else {
				continue; // skip if both debit and credit are missing
			}

			AccountTransaction tx = new AccountTransaction();
			tx.setDate(date);
			tx.setAmount(amount);
			tx.setType(isCredit ? TransactionType.CREDIT : TransactionType.DEBIT);
			tx.setDescription(description);
			tx.setExplanation(explanation);
			tx.setAccount(statement.getAccount());
			tx.setUploadedStatement(statement);
			logger.info("Parsed transaction: date={}, amount={}, type={}, explanation={}", tx.getDate(), tx.getAmount(),
					tx.getType(), tx.getExplanation());
			transactions.add(tx);

		}

		return transactions;
	}

	public static String[] splitTransferDescription(String desc) {
		if (desc == null)
			return new String[] { null, null };

		Pattern pattern = Pattern.compile("^(TRANSFER (TO|FROM) \\d+) - (UPI/.*)$");
		Matcher matcher = pattern.matcher(desc.trim());

		if (matcher.find()) {
			return new String[] { matcher.group(1), // TRANSFER TO/FROM ...
					matcher.group(3) // UPI/...
			};
		} else {
			return new String[] { desc, null }; // No split
		}
	}
}
