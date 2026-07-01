package com.nklmthr.finance.personal.upload.parser;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.*;

import com.nklmthr.finance.personal.enums.TransactionType;
import com.nklmthr.finance.personal.model.AccountTransaction;
import com.nklmthr.finance.personal.model.UploadedStatement;

public class FederalBankStatementParserXLS extends StatementParser {
	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(FederalBankStatementParserXLS.class);
	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	private static final int DATA_START_ROW_INDEX = 21; // Row 22 (0-indexed) is where data starts

	// Column indices
	private static final int COL_DATE = 3;
	private static final int COL_PARTICULARS = 6;
	private static final int COL_WITHDRAWAL = 15;
	private static final int COL_DEPOSIT = 18;

	@Override
	public List<AccountTransaction> parse(InputStream inputStream, UploadedStatement statement) {
		List<AccountTransaction> transactions = new ArrayList<>();

		try (Workbook workbook = createWorkbook(inputStream, statement.getPassword())) {
			Sheet sheet = workbook.getSheetAt(0);

			for (int rowIndex = DATA_START_ROW_INDEX; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
				Row row = sheet.getRow(rowIndex);
				if (row == null) continue;

				if (isFooterRow(row)) {
					logger.info("Reached footer at row {}, stopping", rowIndex);
					break;
				}

				AccountTransaction tx = parseRow(row, statement);
				if (tx != null) {
					transactions.add(tx);
					logger.info("Parsed transaction: date={}, amount={}, type={}, description={}",
							tx.getDate(), tx.getAmount(), tx.getType(), tx.getDescription());
				}
			}

		} catch (IOException e) {
			logger.error("Error reading Federal Bank XLS file", e);
			throw new RuntimeException("Failed to read Excel file", e);
		}

		return transactions;
	}

	private Workbook createWorkbook(InputStream inputStream, String password) throws IOException {
		try {
			if (password != null && !password.isBlank()) {
				return WorkbookFactory.create(inputStream, password);
			}
			return WorkbookFactory.create(inputStream);
		} catch (Exception e) {
			throw new IOException("Unable to read Excel file.", e);
		}
	}

	private AccountTransaction parseRow(Row row, UploadedStatement statement) {
		try {
			Cell dateCell = row.getCell(COL_DATE);
			if (dateCell == null || getCellValueAsString(dateCell).isBlank()) return null;

			String dateStr = getCellValueAsString(dateCell).trim();
			LocalDateTime date = parseDate(dateStr);
			if (date == null) {
				logger.warn("Skipping row {} due to invalid date: {}", row.getRowNum(), dateStr);
				return null;
			}

			Cell particularsCell = row.getCell(COL_PARTICULARS);
			String description = particularsCell != null ? getCellValueAsString(particularsCell).trim() : "";
			description = description.replaceAll("\\s+", " ").trim();

			Cell withdrawalCell = row.getCell(COL_WITHDRAWAL);
			Cell depositCell = row.getCell(COL_DEPOSIT);
			String withdrawalStr = withdrawalCell != null ? getCellValueAsString(withdrawalCell).replace(",", "").trim() : "";
			String depositStr = depositCell != null ? getCellValueAsString(depositCell).replace(",", "").trim() : "";

			BigDecimal amount;
			TransactionType type;

			if (!withdrawalStr.isEmpty() && !withdrawalStr.equals("-")) {
				amount = new BigDecimal(withdrawalStr);
				type = TransactionType.DEBIT;
			} else if (!depositStr.isEmpty() && !depositStr.equals("-")) {
				amount = new BigDecimal(depositStr);
				type = TransactionType.CREDIT;
			} else {
				logger.warn("Skipping row {} with no debit or credit value", row.getRowNum());
				return null;
			}

			AccountTransaction tx = new AccountTransaction();
			tx.setDate(date);
			tx.setAmount(amount);
			tx.setType(type);
			tx.setDescription(description);
			tx.setExplanation(description);
			tx.setAccount(statement.getAccount());
			tx.setUploadedStatement(statement);

			return tx;

		} catch (Exception e) {
			logger.error("Error parsing row {}", row.getRowNum(), e);
			return null;
		}
	}

	private LocalDateTime parseDate(String dateStr) {
		try {
			return LocalDate.parse(dateStr, DATE_FORMATTER).atStartOfDay();
		} catch (Exception e) {
			logger.error("Error parsing date: {}", dateStr, e);
			return null;
		}
	}

	private boolean isFooterRow(Row row) {
		Cell dateCell = row.getCell(COL_DATE);
		if (dateCell == null) return false;
		String val = getCellValueAsString(dateCell).trim().toLowerCase();
		return val.contains("grand total") || val.contains("opening balance") || val.contains("closing balance");
	}

	private String getCellValueAsString(Cell cell) {
		if (cell == null) return "";
		switch (cell.getCellType()) {
			case STRING:
				return cell.getStringCellValue();
			case NUMERIC:
				if (DateUtil.isCellDateFormatted(cell)) {
					return cell.getLocalDateTimeCellValue().format(DATE_FORMATTER);
				}
				double val = cell.getNumericCellValue();
				if (val == Math.floor(val)) return String.valueOf((long) val);
				return String.valueOf(val);
			case BOOLEAN:
				return String.valueOf(cell.getBooleanCellValue());
			case FORMULA:
				FormulaEvaluator evaluator = cell.getSheet().getWorkbook().getCreationHelper().createFormulaEvaluator();
				CellValue cv = evaluator.evaluate(cell);
				switch (cv.getCellType()) {
					case STRING: return cv.getStringValue();
					case NUMERIC:
						double n = cv.getNumberValue();
						return n == Math.floor(n) ? String.valueOf((long) n) : String.valueOf(n);
					default: return "";
				}
			case BLANK:
			default:
				return "";
		}
	}

	@Override
	protected List<AccountTransaction> mapTransactions(List<String[]> rows, UploadedStatement statement) {
		throw new UnsupportedOperationException("CSV parsing not supported for XLS files");
	}
}
