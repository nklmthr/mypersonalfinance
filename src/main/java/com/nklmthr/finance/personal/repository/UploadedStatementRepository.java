package com.nklmthr.finance.personal.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.model.UploadedStatement;

@Repository
public interface UploadedStatementRepository extends JpaRepository<UploadedStatement, String> {

	// Find all statements uploaded by a user
	List<UploadedStatement> findAllByAppUser(AppUser appUser);

	// Find a specific statement for a user by ID
	Optional<UploadedStatement> findByAppUserAndId(AppUser appUser, String id);

	// Delete a specific statement for a user
	void deleteByAppUserAndId(AppUser appUser, String id);

	// (Optional) If needed, check existence
	boolean existsByAppUserAndId(AppUser appUser, String id);
}
