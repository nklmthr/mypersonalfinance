package com.nklmthr.finance.personal;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.*;
import java.util.*;

import org.apache.commons.lang3.BitField;
import org.apache.commons.lang3.StringUtils;

public class DatabaseMigrator {

	

	static class ColumnMapping {
		String sourceTable;
		String sourceColumn;
		String targetTable;
		String targetColumn;

		ColumnMapping(String sTable, String sCol, String tTable, String tCol) {
			sourceTable = sTable;
			sourceColumn = sCol;
			targetTable = tTable;
			targetColumn = tCol;
		}
	}

	static class RowData {
		Object id;
		Object parentId;
		Map<String, Object> columnValues = new LinkedHashMap<>();

		@Override
		public String toString() {
			return "RowData{" + "id=" + id + ", parentId=" + parentId + ", columnValues=" + columnValues + '}';
		}
	}

	public static void main(String[] args) throws Exception {
		Map<String, List<ColumnMapping>> tableMappings = loadMappings("/Users/i344377/Desktop/TableMapping.csv");

		for (String tableKey : tableMappings.keySet()) {
			try (Connection sourceConn = DriverManager.getConnection(SOURCE_DB_URL, SOURCE_USER, SOURCE_PASS);
					Connection targetConn = DriverManager.getConnection(TARGET_DB_URL, TARGET_USER, TARGET_PASS)) {
				List<ColumnMapping> mappings = tableMappings.get(tableKey);

				if (hasSelfReference(mappings)) {
					migrateWithParentHandling(sourceConn, targetConn, mappings);
				} else {
					migrateSimpleTable(sourceConn, targetConn, mappings);
				}
				System.out.println("✅ Migration complete for: " + tableKey);
			} catch (Exception e) {
				System.err.println("❌ Error migrating table: " + tableKey);
				e.printStackTrace();
			}
		}
	}

	private static Map<String, List<ColumnMapping>> loadMappings(String csvFile) throws IOException {
		Map<String, List<ColumnMapping>> map = new LinkedHashMap<>();
		try (BufferedReader reader = new BufferedReader(new FileReader(csvFile))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.trim().isEmpty() || line.startsWith("sourceTable"))
					continue;
				String[] tokens = line.split(",");
				System.out.println("Processing mapping: " + line);
				if (StringUtils.isAnyBlank(tokens) || tokens.length < 4) {
					System.err.println("Invalid mapping line: " + line);
					continue;
				}
				String sTable = tokens[0].trim().replace("\uFEFF", "");
				String sCol = tokens[1].trim();
				String tTable = tokens[2].trim().replace("\uFEFF", "");
				String tCol = tokens[3].trim();
				ColumnMapping mapping = new ColumnMapping(sTable, sCol, tTable, tCol);
				map.computeIfAbsent(tTable, k -> new ArrayList<>()).add(mapping);
			}
		}
		return map;
	}

	private static boolean hasSelfReference(List<ColumnMapping> mappings) {
		return mappings.stream().anyMatch(m -> "parent_id".equalsIgnoreCase(m.targetColumn));
	}

	private static void migrateSimpleTable(Connection sourceConn, Connection targetConn, List<ColumnMapping> mappings)
			throws SQLException {
		if (mappings.isEmpty())
			return;

		String sourceTable = mappings.get(0).sourceTable;
		String targetTable = mappings.get(0).targetTable;
		List<String> sourceCols = new ArrayList<>();
		List<String> targetCols = new ArrayList<>();
		for (ColumnMapping mapping : mappings) {
			sourceCols.add(mapping.sourceColumn);
			targetCols.add(mapping.targetColumn);
		}
		targetCols.add("app_user_id");

		String selectSql = "SELECT " + String.join(",", sourceCols) + " FROM " + sourceTable;
		System.out.println("→ " + targetTable + " | " + selectSql);

		List<Map<String, Object>> rows = new ArrayList<>();
		try (PreparedStatement stmt = sourceConn.prepareStatement(selectSql); ResultSet rs = stmt.executeQuery()) {
			while (rs.next()) {
				Map<String, Object> row = new LinkedHashMap<>();
				for (String col : sourceCols)
					row.put(col, rs.getObject(col));
				rows.add(row);
			}
		}

		String insertSql = "INSERT INTO " + targetTable + " (" + String.join(",", targetCols) + ") VALUES ("
				+ "?,".repeat(targetCols.size()).replaceAll(",$", ")");
		try (PreparedStatement stmt = targetConn.prepareStatement(insertSql)) {
			for (Map<String, Object> row : rows) {
				int i = 1;
				for (String col : targetCols) {
					if ("app_user_id".equals(col)) {
						stmt.setObject(i++, HARD_CODED_USER_ID);
					} else {
						String sourceCol = mappings.stream().filter(m -> m.targetColumn.equals(col)).findFirst()
								.get().sourceColumn;
						stmt.setObject(i++, row.get(sourceCol));
					}
				}
				stmt.addBatch();
			}
			stmt.executeBatch();
		}
	}

	private static void migrateWithParentHandling(Connection sourceConn, Connection targetConn,
			List<ColumnMapping> mappings) throws SQLException {
		String sourceTable = mappings.get(0).sourceTable;
		String targetTable = mappings.get(0).targetTable;
		List<String> sourceCols = new ArrayList<>();
		List<String> targetCols = new ArrayList<>();
		for (ColumnMapping mapping : mappings) {
			sourceCols.add(mapping.sourceColumn);
			targetCols.add(mapping.targetColumn);
		}
		targetCols.add("app_user_id");

		String selectSql = "SELECT " + String.join(",", sourceCols) + " FROM " + sourceTable;
		System.out.println("→ " + targetTable + " | " + selectSql);

		Map<Object, RowData> rowMap = new LinkedHashMap<>();
		try (PreparedStatement stmt = sourceConn.prepareStatement(selectSql); ResultSet rs = stmt.executeQuery()) {
			while (rs.next()) {
				RowData row = new RowData();
				for (ColumnMapping mapping : mappings) {
					Object val = rs.getObject(mapping.sourceColumn);
					row.columnValues.put(mapping.targetColumn, val);
					if ("id".equalsIgnoreCase(mapping.targetColumn))
						row.id = val;
					if ("parent_id".equalsIgnoreCase(mapping.targetColumn))
						row.parentId = val;
				}
				rowMap.put(row.id, row);
			}
		}

		List<RowData> sorted = new ArrayList<>();
		Set<Object> visited = new HashSet<>();
		for (RowData row : rowMap.values())
			dfs(row, rowMap, visited, sorted);

		String placeholders = String.join(",", Collections.nCopies(targetCols.size(), "?"));
		String insertSql = "INSERT INTO " + targetTable + " (" + String.join(",", targetCols) + ") VALUES ("
				+ placeholders + ")";
		System.out.println("→ " + targetTable + " | " + insertSql);
		try (PreparedStatement stmt = targetConn.prepareStatement(insertSql)) {
			for (RowData row : sorted) {
				int i = 1;
				for (String col : targetCols) {
					if ("app_user_id".equals(col)) {
						stmt.setObject(i++, HARD_CODED_USER_ID);
					} else if ("type".equals(col)) {
						Object typeValue = row.columnValues.get(col);
						if (typeValue == null || "0".equals(typeValue.toString())
								|| "DEBIT".equalsIgnoreCase(typeValue.toString())) {
							stmt.setObject(i++, "DEBIT");
						} else {
							stmt.setObject(i++, "CREDIT");
						}
					} else if ("source_time".equals(col)) {
						Object val = row.columnValues.get(col);
						if (val instanceof Number) {
							stmt.setTimestamp(i++, new Timestamp(((Number) val).longValue()));
						} else if (val instanceof String && ((String) val).matches("\\d+")) {
							stmt.setTimestamp(i++, new Timestamp(Long.parseLong((String) val)));
						} else {
							stmt.setNull(i++, Types.TIMESTAMP);
						}
					} else {
						stmt.setObject(i++, row.columnValues.get(col));
					}
				}

				stmt.executeUpdate();
			}

		}
	}

	private static void dfs(RowData row, Map<Object, RowData> rowMap, Set<Object> visited, List<RowData> sorted) {
		if (visited.contains(row.id))
			return;
		if (row.parentId != null && rowMap.containsKey(row.parentId)) {
			dfs(rowMap.get(row.parentId), rowMap, visited, sorted);
		}
		visited.add(row.id);
		sorted.add(row);
	}
}