package com.nklmthr.finance.personal.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nklmthr.finance.personal.model.Attachment;

@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, String> {
	List<Attachment> findByAccountTransaction_IdAndAccountTransaction_AppUser_Id(String transactionId,
			String appUserId);

	Optional<Attachment> findByIdAndAppUser_Id(String id, String appUserId);

	/**
	 * Batch attachment count grouped by transaction id. Returns rows of
	 * {@code [transactionId (String), count (Long)]} only for transactions that
	 * have at least one attachment — callers should default missing ids to 0.
	 *
	 * Used by the transactions list endpoint to render a distinct icon on rows
	 * that already have receipts, without N+1 lookups per row.
	 */
	@Query("SELECT a.accountTransaction.id, COUNT(a) "
			+ "FROM Attachment a "
			+ "WHERE a.accountTransaction.id IN :transactionIds "
			+ "GROUP BY a.accountTransaction.id")
	List<Object[]> countByTransactionIdIn(@Param("transactionIds") List<String> transactionIds);
}
