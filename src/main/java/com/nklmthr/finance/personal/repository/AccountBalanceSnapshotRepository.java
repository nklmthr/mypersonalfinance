package com.nklmthr.finance.personal.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nklmthr.finance.personal.model.AccountBalanceSnapshot;

public interface AccountBalanceSnapshotRepository extends JpaRepository<AccountBalanceSnapshot, String> {
	
	@Query("""
		    SELECT abs FROM AccountBalanceSnapshot abs
		    LEFT JOIN FETCH abs.account acc
		    LEFT JOIN FETCH acc.accountType
		    LEFT JOIN FETCH acc.institution
		    WHERE MONTH(abs.snapshotDate) = :month AND YEAR(abs.snapshotDate) = :year
		""")
	List<AccountBalanceSnapshot> findByMonthAndYear(@Param("month") int month, @Param("year") int year);

	boolean existsByAccountIdAndSnapshotDateAfter(String accountId, LocalDateTime snapshotDate);
}