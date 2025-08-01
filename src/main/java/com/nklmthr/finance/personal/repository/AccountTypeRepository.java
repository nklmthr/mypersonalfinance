package com.nklmthr.finance.personal.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nklmthr.finance.personal.model.AccountType;
import com.nklmthr.finance.personal.model.AppUser;

@Repository
public interface AccountTypeRepository extends JpaRepository<AccountType, String> {

	Optional<AccountType> findByAppUserAndName(AppUser appUser, String name);

	boolean existsByAppUserAndName(AppUser appUser, String name);

	boolean existsByAppUserAndId(AppUser appUser, String id);

	List<AccountType> findByAppUser(AppUser appUser);

	Optional<AccountType> findByAppUserAndId(AppUser appUser, String id);

	void deleteByAppUserAndId(AppUser appUser, String id);
}
