package com.nklmthr.finance.personal.dto;

import java.util.List;

public record BulkUpdateResponse(
	int requested,
	int updated,
	List<SkippedItem> skipped
) {
	public record SkippedItem(String id, String reason) {}
}
