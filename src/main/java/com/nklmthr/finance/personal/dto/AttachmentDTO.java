package com.nklmthr.finance.personal.dto;

import java.util.Date;

public record AttachmentDTO(
	String id,
	String fileName,
	String contentType,
	Date date,
	long size,
	boolean hasThumbnail,
	String accountTransactionId
) {
}
