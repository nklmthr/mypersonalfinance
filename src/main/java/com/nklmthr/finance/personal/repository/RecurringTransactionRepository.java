package com.nklmthr.finance.personal.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.model.RecurringTransaction;

@Repository
public interface RecurringTransactionRepository extends JpaRepository<RecurringTransaction, String> {

	List<RecurringTransaction> findByAppUser(AppUser appUser);

	List<RecurringTransaction> findByAppUserAndEnabledTrue(AppUser appUser);

	Optional<RecurringTransaction> findByAppUserAndId(AppUser appUser, String id);
}
