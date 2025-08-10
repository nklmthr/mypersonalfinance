package com.nklmthr.finance.personal.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nklmthr.finance.personal.enums.TransactionType;
import com.nklmthr.finance.personal.model.AccountTransaction;
import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.model.UploadedStatement;

@Repository
public interface AccountTransactionRepository
		extends JpaRepository<AccountTransaction, String>, JpaSpecificationExecutor<AccountTransaction> {

	@EntityGraph(attributePaths = { "category", "category.parent", // <-- add this
			"account", "account.accountType", "account.institution", "children", "children.category",
			"children.category.parent" // <-- and this
	})
	List<AccountTransaction> findByAppUserAndParentId(AppUser appUser, String parentId);

	@EntityGraph(attributePaths = { "category", "category.parent", // <-- add this
			"account", "account.accountType", "account.institution", "children", "children.category",
			"children.category.parent" // <-- and this
	})
	List<AccountTransaction> findByAppUserAndAccountId(AppUser appUser, String accountId);

	@EntityGraph(attributePaths = { "category", "category.parent", // <-- add this
			"account", "account.accountType", "account.institution", "children", "children.category",
			"children.category.parent" // <-- and this
	})
	@Query("SELECT t FROM AccountTransaction t WHERE t.appUser = :appUser AND FUNCTION('MONTH', t.date) = :month AND FUNCTION('YEAR', t.date) = :year")
	List<AccountTransaction> findByAppUserAndMonthAndYear(@Param("appUser") AppUser appUser, @Param("month") int month,
			@Param("year") int year);

	@EntityGraph(attributePaths = { "category", "category.parent", // <-- add this
			"account", "account.accountType", "account.institution", "children", "children.category",
			"children.category.parent" // <-- and this
	})
	List<AccountTransaction> findByAppUserAndSourceThreadId(AppUser appUser, String sourceThreadId);

	@EntityGraph(attributePaths = { "category", "category.parent", // <-- add this
			"account", "account.accountType", "account.institution", "children", "children.category",
			"children.category.parent" // <-- and this
	})
	Page<AccountTransaction> findAll(Specification<AccountTransaction> spec, Pageable pageable);

	@EntityGraph(attributePaths = { "category", "category.parent", // <-- add this
			"account", "account.accountType", "account.institution", "children", "children.category",
			"children.category.parent" // <-- and this
	})
	List<AccountTransaction> findAll(Specification<AccountTransaction> spec);

	@EntityGraph(attributePaths = { "category", "category.parent", // <-- add this
			"account", "account.accountType", "account.institution", "children", "children.category",
			"children.category.parent" // <-- and this
	})
	List<AccountTransaction> findAll(Specification<AccountTransaction> spec, Sort sort);

	@EntityGraph(attributePaths = { "category", "category.parent", // <-- add this
			"account", "account.accountType", "account.institution", "children", "children.category",
			"children.category.parent" // <-- and this
	})
	Optional<AccountTransaction> findByAppUserAndId(AppUser appUser, String id);

	void deleteByAppUserAndId(AppUser appUser, String id);

	@EntityGraph(attributePaths = { "category", "category.parent", // <-- add this
			"account", "account.accountType", "account.institution", "children", "children.category",
			"children.category.parent" // <-- and this
	})
	List<AccountTransaction> findByAppUserAndUploadedStatement(AppUser appUser, UploadedStatement statement);

	void deleteAllByAppUserAndIdIn(AppUser appUser, List<String> list);

	@EntityGraph(attributePaths = { "category", "category.parent", // <-- add this
			"account", "account.accountType", "account.institution", "children", "children.category",
			"children.category.parent" // <-- and this
	})
	@Query("SELECT t FROM AccountTransaction t " + "WHERE t.appUser = :user " + "AND t.description = :description "
			+ "AND t.type = :type " + "AND t.amount = :amount " + "AND ABS(TIMESTAMPDIFF(SECOND, t.date, :date)) <= 60")
	List<AccountTransaction> findSimilarTransactionWithinOneMinute(@Param("user") AppUser user,
			@Param("description") String description, @Param("type") TransactionType type,
			@Param("amount") BigDecimal amount, @Param("date") LocalDateTime date);

	@Query(value = """
			SELECT
			    c.id AS categoryId,
			    c.name AS categoryName,
			    c.parent_id AS parentId,
			    DATE_FORMAT(t.date, '%Y-%m') AS month,
			    COALESCE(SUM(CASE
			        WHEN t.type = 'CREDIT' THEN t.amount
			        WHEN t.type = 'DEBIT' THEN -t.amount
			        ELSE 0
			    END), 0) AS total
			FROM categories c
			LEFT JOIN account_transactions t
			    ON c.id = t.category_id
			    AND t.app_user_id = :userId
			    AND t.date >= :startDate
			WHERE c.id NOT IN (:excludedCategoryIds)
			GROUP BY c.id, DATE_FORMAT(t.date, '%Y-%m')
			ORDER BY c.name, month
			""", nativeQuery = true)
	List<CategoryMonthlyProjection> getCategoryMonthlySpend(@Param("userId") String userId,
			@Param("startDate") LocalDate startDate, @Param("excludedCategoryIds") List<String> excludedCategoryIds);

	@Query("""
			    SELECT COALESCE(SUM(
			        CASE
			            WHEN t.type = 'CREDIT' THEN t.amount
			            WHEN t.type = 'DEBIT'  THEN -t.amount
			            ELSE 0
			        END
			    ), 0)
			    FROM AccountTransaction t
			    WHERE (:month IS NULL OR :month = '' OR FUNCTION('DATE_FORMAT', t.date, '%Y-%m') = :month)
			      AND (:accountId IS NULL OR :accountId = '' OR t.account.id = :accountId)
			      AND (:type IS NULL OR :type = '' OR :type = 'ALL' OR t.type = :type)
			      AND (:search IS NULL OR :search = '' OR LOWER(t.description) LIKE LOWER(CONCAT('%', :search, '%')))
			      AND (COALESCE(:categoryIds, NULL) IS NULL OR t.category.id IN :categoryIds)
			      AND t.parent IS NULL
			""")
	BigDecimal getCurrentTotal(@Param("month") String month, @Param("accountId") String accountId,
			@Param("type") TransactionType transactionType, @Param("search") String search,
			@Param("categoryIds") Set<String> categoryIds);
}
