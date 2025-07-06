package com.nklmthr.finance.personal.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nklmthr.finance.personal.model.AccountBalanceSnapshot;

public interface AccountBalanceSnapshotRepository extends JpaRepository<AccountBalanceSnapshot, Long> {
	
	@Query("SELECT s FROM AccountBalanceSnapshot s WHERE FUNCTION('MONTH', s.snapshotDate) = :month AND FUNCTION('YEAR', s.snapshotDate) = :year")
	List<AccountBalanceSnapshot> findByMonthAndYear(@Param("month") int month, @Param("year") int year);

	boolean existsByAccountIdAndSnapshotDateAfter(Long accountId, LocalDate snapshotDate);
}