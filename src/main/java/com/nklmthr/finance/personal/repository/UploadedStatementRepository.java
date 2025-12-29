package com.nklmthr.finance.personal.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.model.UploadedStatement;

@Repository
public interface UploadedStatementRepository extends JpaRepository<UploadedStatement, String> {

	// Find all statements uploaded by a user, sorted by status (UPLOADED, FAILED, PROCESSED) then by date desc
	@Query("""
		SELECT s FROM UploadedStatement s 
		WHERE s.appUser = :appUser 
		ORDER BY 
			CASE s.status 
				WHEN 'UPLOADED' THEN 1 
				WHEN 'FAILED' THEN 2 
				WHEN 'PROCESSED' THEN 3 
				ELSE 4 
			END,
			s.uploadedAt DESC
	""")
	List<UploadedStatement> findAllByAppUser(@Param("appUser") AppUser appUser);

	// Find a specific statement for a user by ID
	Optional<UploadedStatement> findByAppUserAndId(AppUser appUser, String id);

	// Delete a specific statement for a user
	void deleteByAppUserAndId(AppUser appUser, String id);

	// (Optional) If needed, check existence
	boolean existsByAppUserAndId(AppUser appUser, String id);
}
