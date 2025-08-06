package com.nklmthr.finance.personal.repository;

public interface CategoryMonthlyProjection {
	String getCategoryId();

	String getCategoryName();

	String getParentId();

	String getMonth();

	Double getTotal();

	default String stringify() {
		return String.format(
				"CategoryMonthlyProjection[categoryId=%s, categoryName=%s, parentId=%s, month=%s, total=%.2f]",
				getCategoryId(), getCategoryName(), getParentId(), getMonth(), getTotal());
	}
}
