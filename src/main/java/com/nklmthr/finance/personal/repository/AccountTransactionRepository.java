package com.nklmthr.finance.personal.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nklmthr.finance.personal.enums.TransactionType;
import com.nklmthr.finance.personal.model.AccountTransaction;
import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.model.UploadedStatement;

@Repository
public interface AccountTransactionRepository extends JpaRepository<AccountTransaction, String> {

	// Corrected to Spring Data method naming conventions
	List<AccountTransaction> findByAppUserAndParentId(AppUser appUser, String parentId);

	List<AccountTransaction> findByAppUserAndAccountId(AppUser appUser, String accountId);

	@EntityGraph(attributePaths = { "category" })
	@Query("SELECT t FROM AccountTransaction t WHERE t.appUser = :appUser AND FUNCTION('MONTH', t.date) = :month AND FUNCTION('YEAR', t.date) = :year")
	List<AccountTransaction> findByAppUserAndMonthAndYear(@Param("appUser") AppUser appUser, @Param("month") int month,
			@Param("year") int year);

	@EntityGraph(attributePaths = { "category", "account", "account.accountType", "account.institution" })
	Page<AccountTransaction> findAll(Specification<AccountTransaction> spec, Pageable pageable);

	List<AccountTransaction> findByAppUserAndSourceThreadId(AppUser appUser, String sourceThreadId);

	@EntityGraph(attributePaths = { "category", "account", "account.accountType", "account.institution" })
	List<AccountTransaction> findAll(Specification<AccountTransaction> spec, Sort sort);

	Optional<AccountTransaction> findByAppUserAndId(AppUser appUser, String id);

	void deleteByAppUserAndId(AppUser appUser, String id);

	List<AccountTransaction> findByAppUserAndUploadedStatement(AppUser appUser, UploadedStatement statement);

	void deleteAllByAppUserAndIdIn(AppUser appUser, List<String> list);

	@Query("SELECT t FROM AccountTransaction t " + "WHERE t.appUser = :user " + "AND t.description = :description "
			+ "AND t.type = :type " + "AND t.amount = :amount " + "AND ABS(TIMESTAMPDIFF(SECOND, t.date, :date)) <= 60")
	List<AccountTransaction> findSimilarTransactionWithinOneMinute(@Param("user") AppUser user,
			@Param("description") String description, @Param("type") TransactionType type,
			@Param("amount") BigDecimal amount, @Param("date") LocalDateTime date);

}
