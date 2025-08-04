package com.nklmthr.finance.personal.repository;

public interface CategoryMonthlyProjection {
	String getCategoryId();

	String getCategoryName();

	String getParentId();

	String getMonth();

	Double getTotal();
}
