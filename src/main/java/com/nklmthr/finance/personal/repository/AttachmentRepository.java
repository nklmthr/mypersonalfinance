package com.nklmthr.finance.personal.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nklmthr.finance.personal.model.Attachment;

@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, String> {
	List<Attachment> findByAccountTransaction_IdAndAccountTransaction_AppUser_Id(String transactionId,
			String appUserId);

}
