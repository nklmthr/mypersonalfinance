package com.nklmthr.finance.personal.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.model.Institution;

@Repository
public interface InstitutionRepository extends JpaRepository<Institution, String> {

	Optional<Institution> findByAppUserAndName(AppUser appUser, String name);

	boolean existsByAppUserAndName(AppUser appUser, String name);

	List<Institution> findAllByAppUser(AppUser appUser);

	Optional<Institution> findByAppUserAndId(AppUser appUser, String id);

	void deleteByAppUserAndId(AppUser appUser, String id);
}
