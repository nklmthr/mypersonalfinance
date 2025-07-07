package com.nklmthr.finance.personal.scheduler;

import java.util.Arrays;
import java.util.List;

import com.nklmthr.finance.personal.model.AccountTransaction;

public class SBICCDataExtractionServiceImpl extends AbstractDataExtractionService {

	@Override
	protected List<String> getEmailSubject() {
		return Arrays.asList("Transaction Alert from SBI Card");
	}

	@Override
	protected String getSender() {
		return "onlinesbicard@sbicard.com";
	}

	public static void main(String[] args) {
		SBICCDataExtractionServiceImpl impl = new SBICCDataExtractionServiceImpl();
		impl.run();
	}

	@Override
	protected AccountTransaction extractTransactionData(String emailContent) {
		// TODO Auto-generated method stub
		return null;
	}
}
