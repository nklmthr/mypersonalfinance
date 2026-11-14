package com.nklmthr.finance.personal.exception;

public class TransferDestinationDeleteException extends RuntimeException {
	private final String sourceTransactionId;

	public TransferDestinationDeleteException(String message, String sourceTransactionId) {
		super(message);
		this.sourceTransactionId = sourceTransactionId;
	}

	public String getSourceTransactionId() {
		return sourceTransactionId;
	}
}
