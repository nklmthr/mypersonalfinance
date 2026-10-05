package com.nklmthr.finance.personal.enums;

public enum RecurrenceType {
	MONTHLY,
	QUARTERLY,      // Jan, Apr, Jul, Oct (months 1,4,7,10)
	HALF_YEARLY,    // Jan, Jul (months 1,7)
	YEARLY,         // one specific month
	CUSTOM          // user-defined comma-separated months
}
