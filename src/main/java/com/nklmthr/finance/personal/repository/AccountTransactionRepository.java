package com.nklmthr.finance.personal.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nklmthr.finance.personal.model.AccountTransaction;

@Repository
public interface AccountTransactionRepository extends JpaRepository<AccountTransaction, String> {

	List<AccountTransaction> findByParentId(String parentId);

	List<AccountTransaction> findByAccountId(String accountId);

	@Query("SELECT t FROM AccountTransaction t WHERE FUNCTION('MONTH', t.date) = :month AND FUNCTION('YEAR', t.date) = :year")
	List<AccountTransaction> findByMonthAndYear(@Param("month") int month, @Param("year") int year);

	@EntityGraph(attributePaths = { "category", "account", "account.accountType",
			"account.institution" })
	@Query("SELECT t FROM AccountTransaction t WHERE t.parent IS NULL ORDER BY t.date DESC")
	List<AccountTransaction> findAllWithGraph();

	Optional<AccountTransaction> findBySourceThreadId(String sourceThreadId);
}
