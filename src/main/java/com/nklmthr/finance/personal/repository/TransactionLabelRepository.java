package com.nklmthr.finance.personal.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nklmthr.finance.personal.model.TransactionLabel;

@Repository
public interface TransactionLabelRepository extends JpaRepository<TransactionLabel, String> {

	List<TransactionLabel> findByTransactionIdAndAppUserId(String transactionId, String appUserId);
	
	List<TransactionLabel> findByLabelIdAndAppUserId(String labelId, String appUserId);
	
	@Modifying
	@Query("DELETE FROM TransactionLabel tl WHERE tl.transaction.id = :transactionId AND tl.appUser.id = :appUserId")
	void deleteByTransactionIdAndAppUserId(@Param("transactionId") String transactionId, @Param("appUserId") String appUserId);
	
	@Modifying
	@Query("DELETE FROM TransactionLabel tl WHERE tl.label.id = :labelId AND tl.appUser.id = :appUserId")
	void deleteByLabelIdAndAppUserId(@Param("labelId") String labelId, @Param("appUserId") String appUserId);
}

