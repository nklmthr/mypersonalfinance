package com.nklmthr.finance.personal.upload.parser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import com.nklmthr.finance.personal.model.AccountTransaction;
import com.nklmthr.finance.personal.model.UploadedStatement;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

public abstract class StatementParser {

	public List<AccountTransaction> parse(InputStream csvInputStream, UploadedStatement statement) {
		List<String[]> rows = readCsv(csvInputStream);
		return mapTransactions(rows, statement);
	}

	// Reads the CSV file into a list of string arrays using OpenCSV
	protected List<String[]> readCsv(InputStream inputStream) {
		List<String[]> rows = new ArrayList<>();
		try (CSVReader csvReader = new CSVReader(new InputStreamReader(inputStream))) {
			String[] row;
			while ((row = csvReader.readNext()) != null) {
				if (isValidRow(row)) {
					rows.add(row);
				}
			}
		} catch (IOException | CsvValidationException e) {
			throw new RuntimeException("Failed to read CSV file", e);
		}
		return rows;
	}

	protected boolean isValidRow(String[] row) {
		return row.length > 1 && !row[0].isBlank();
	}

	// Must be implemented by subclasses to map rows to domain objects
	protected abstract List<AccountTransaction> mapTransactions(List<String[]> rows, UploadedStatement statement);
}
