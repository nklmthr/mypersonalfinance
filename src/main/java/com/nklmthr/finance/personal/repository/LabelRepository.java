package com.nklmthr.finance.personal.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.model.Label;

public interface LabelRepository extends JpaRepository<Label, String> {

	Optional<Label> findByAppUserAndNameIgnoreCase(AppUser appUser, String name);

	List<Label> findByAppUserAndNameContainingIgnoreCase(AppUser appUser, String search, Pageable pageable);

	List<Label> findByAppUser(AppUser appUser);

}

