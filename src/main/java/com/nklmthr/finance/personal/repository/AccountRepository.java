package com.nklmthr.finance.personal.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nklmthr.finance.personal.model.Account;
import com.nklmthr.finance.personal.model.AppUser;

@Repository
public interface AccountRepository extends JpaRepository<Account, String> {

	List<Account> findByAppUserAndAccountTypeId(AppUser user, String accountTypeId);

	List<Account> findByAppUserAndInstitutionId(AppUser user, String institutionId);

	List<Account> findByAppUserAndAccountTypeIdAndInstitutionId(AppUser user, String accountTypeId,
			String institutionId);

	Optional<Account> findByAppUserAndName(AppUser appUser, String string);

	List<Account> findAllByAppUser(AppUser appUser, Sort sort);

	Optional<Account> findByAppUserAndId(AppUser appUser, String id);
}
