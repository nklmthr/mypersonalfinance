package com.nklmthr.finance.personal.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nklmthr.finance.personal.model.AccountBalanceSnapshot;
import com.nklmthr.finance.personal.model.AppUser;

public interface AccountBalanceSnapshotRepository extends JpaRepository<AccountBalanceSnapshot, String> {
	
	@Query("""
		SELECT abs FROM AccountBalanceSnapshot abs
		LEFT JOIN FETCH abs.account acc
		LEFT JOIN FETCH acc.accountType
		LEFT JOIN FETCH acc.institution
		WHERE abs.appUser = :appUser AND MONTH(abs.snapshotDate) = :month AND YEAR(abs.snapshotDate) = :year
	""")
	List<AccountBalanceSnapshot> findByAppUserAndMonthAndYear(
	    @Param("appUser") AppUser appUser,
	    @Param("month") int month,
	    @Param("year") int year
	);

	boolean existsByAppUserAndAccountIdAndSnapshotDateAfter(
	    AppUser appUser,
	    String accountId,
	    LocalDateTime snapshotDate
	);

	List<AccountBalanceSnapshot> findAllByAppUser(AppUser appUser);
}
