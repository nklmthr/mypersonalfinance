package com.nklmthr.finance.personal.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

import org.springframework.data.jpa.domain.Specification;

import com.nklmthr.finance.personal.enums.TransactionType;
import com.nklmthr.finance.personal.model.AccountTransaction;
import com.nklmthr.finance.personal.model.AppUser;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

public class AccountTransactionSpecifications {

	public static Specification<AccountTransaction> hasAccount(String accountId) {
		return (root, query, cb) -> cb.equal(root.get("account").get("id"), accountId);
	}

	public static Specification<AccountTransaction> hasTransactionType(TransactionType type) {
		return (root, query, cb) -> cb.equal(root.get("type"), type);
	}

	public static Specification<AccountTransaction> dateBetween(LocalDateTime start, LocalDateTime end) {
		return (root, query, cb) -> cb.between(root.get("date"), start, end);
	}

	public static Specification<AccountTransaction> isRootTransaction() {
		return (root, query, cb) -> cb.isNull(root.get("parent"));
	}
	
	public static Specification<AccountTransaction> isLeafTransaction() {
		return (root, query, cb) -> {
			// A leaf transaction is either:
			// 1. A root transaction with no children (parent IS NULL)
			// 2. A child transaction (parent IS NOT NULL)
			// In other words: exclude transactions that have children
			// We can detect "has children" by checking if this transaction appears as a parent
			
			// Subquery to check if transaction has children
			var subquery = query.subquery(Long.class);
			var subRoot = subquery.from(AccountTransaction.class);
			subquery.select(cb.count(subRoot));
			subquery.where(cb.equal(subRoot.get("parent"), root.get("id")));
			
			// Return transactions that have 0 children
			return cb.equal(subquery, 0L);
		};
	}

	public static Specification<AccountTransaction> hasCategory(Set<String> categoryIds) {
		return (root, query, cb) -> root.get("category").get("id").in(categoryIds);
	}

	public static Specification<AccountTransaction> matchesSearch(String search) {
		return (Root<AccountTransaction> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
			Predicate combined;

			try {
				BigDecimal searchAmount = new BigDecimal(search);
				BigDecimal lower = searchAmount.subtract(BigDecimal.valueOf(100));
				BigDecimal upper = searchAmount.add(BigDecimal.valueOf(100));
				Predicate amountMatch = cb.between(root.get("amount"), lower, upper);

				// You can also optionally combine this with text search if desired
				combined = amountMatch;
			} catch (NumberFormatException e) {
				String likeSearch = "%" + search.toLowerCase() + "%";

				Predicate descriptionMatch = cb.like(cb.lower(root.get("description")), likeSearch);
				Predicate explanationMatch = cb.like(cb.lower(root.get("explanation")), likeSearch);
				Predicate typeMatch = cb.like(cb.lower(root.get("type").as(String.class)), likeSearch);
				Predicate accountMatch = cb.like(cb.lower(root.get("account").get("name")), likeSearch);
				Predicate categoryMatch = cb.like(cb.lower(root.get("category").get("name")), likeSearch);

				combined = cb.or(descriptionMatch, explanationMatch, typeMatch, accountMatch, categoryMatch);
			}

			return combined;
		};
	}

	public static Specification<AccountTransaction> belongsToUser(AppUser appUser) {
		return (root, query, cb) -> cb.equal(root.get("appUser"), appUser);
	}
}
