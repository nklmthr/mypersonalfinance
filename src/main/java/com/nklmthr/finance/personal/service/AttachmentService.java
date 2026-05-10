package com.nklmthr.finance.personal.service;

import java.io.IOException;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.nklmthr.finance.personal.dto.AttachmentDTO;
import com.nklmthr.finance.personal.model.AccountTransaction;
import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.model.Attachment;
import com.nklmthr.finance.personal.repository.AccountTransactionRepository;
import com.nklmthr.finance.personal.repository.AttachmentRepository;

import jakarta.transaction.Transactional;

@Service
public class AttachmentService {

	private static final Logger logger = LoggerFactory.getLogger(AttachmentService.class);

	private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024; // 10 MB

	@Autowired
	private AttachmentRepository attachmentRepository;

	@Autowired
	private AccountTransactionRepository accountTransactionRepository;

	@Autowired
	private AppUserService appUserService;

	public List<AttachmentDTO> listForTransaction(String transactionId) {
		AppUser appUser = appUserService.getCurrentUser();
		// Verify the transaction belongs to the current user before exposing attachments
		accountTransactionRepository.findByAppUserAndId(appUser, transactionId)
				.orElseThrow(() -> new IllegalArgumentException("Transaction not found for user"));

		List<Attachment> list = attachmentRepository
				.findByAccountTransaction_IdAndAccountTransaction_AppUser_Id(transactionId, appUser.getId());
		return list.stream().map(this::toDTO).toList();
	}

	@Transactional
	public AttachmentDTO upload(String transactionId, MultipartFile file) throws IOException {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("Uploaded file is empty");
		}
		if (file.getSize() > MAX_FILE_SIZE_BYTES) {
			throw new IllegalArgumentException("File too large. Max size is 10 MB.");
		}
		String contentType = file.getContentType();
		// Some browsers (esp. drag-drop) report application/octet-stream even for known
		// image formats; fall back to inferring from the filename extension so we store
		// a useful Content-Type for later inline rendering.
		if (contentType == null || contentType.isBlank()
				|| "application/octet-stream".equalsIgnoreCase(contentType.trim())) {
			String inferred = guessContentTypeFromFilename(file.getOriginalFilename());
			if (inferred != null) {
				contentType = inferred;
			}
		}
		if (contentType == null || !(contentType.startsWith("image/") || "application/pdf".equals(contentType))) {
			throw new IllegalArgumentException(
					"Unsupported file type. Only images (jpeg/png/gif/webp) and PDFs are allowed.");
		}

		AppUser appUser = appUserService.getCurrentUser();
		AccountTransaction transaction = accountTransactionRepository.findByAppUserAndId(appUser, transactionId)
				.orElseThrow(() -> new IllegalArgumentException("Transaction not found for user"));

		byte[] content = file.getBytes();
		byte[] thumbnail = null;
		try {
			thumbnail = Attachment.generateThumbnail(contentType, content);
		} catch (Exception e) {
			logger.warn("Failed to generate thumbnail for attachment '{}' (contentType={}): {}",
					file.getOriginalFilename(), contentType, e.getMessage());
		}

		Attachment attachment = Attachment.builder()
				.date(new Date())
				.fileName(file.getOriginalFilename())
				.contentType(contentType)
				.content(content)
				.thumbnailData(thumbnail)
				.accountTransaction(transaction)
				.appUser(appUser)
				.build();

		Attachment saved = attachmentRepository.save(attachment);
		logger.info("Saved attachment id={} for transaction id={} ({} bytes, type={})",
				saved.getId(), transactionId, content.length, contentType);
		return toDTO(saved);
	}

	public Attachment getOwnedAttachment(String attachmentId) {
		AppUser appUser = appUserService.getCurrentUser();
		return attachmentRepository.findByIdAndAppUser_Id(attachmentId, appUser.getId())
				.orElseThrow(() -> new IllegalArgumentException("Attachment not found"));
	}

	@Transactional
	public void delete(String attachmentId) {
		Attachment attachment = getOwnedAttachment(attachmentId);
		attachmentRepository.delete(attachment);
		logger.info("Deleted attachment id={} (transactionId={})", attachmentId,
				attachment.getAccountTransaction() != null ? attachment.getAccountTransaction().getId() : null);
	}

	private AttachmentDTO toDTO(Attachment a) {
		long size = a.getContent() != null ? a.getContent().length : 0L;
		boolean hasThumb = a.getThumbnailData() != null && a.getThumbnailData().length > 0;
		String txId = a.getAccountTransaction() != null ? a.getAccountTransaction().getId() : null;
		// Surface a usable Content-Type to the frontend even for legacy rows that may
		// have been saved with a missing or generic value.
		String resolvedType = a.getContentType();
		if (resolvedType == null || resolvedType.isBlank()
				|| "application/octet-stream".equalsIgnoreCase(resolvedType.trim())) {
			String inferred = guessContentTypeFromFilename(a.getFileName());
			if (inferred != null) {
				resolvedType = inferred;
			}
		}
		return new AttachmentDTO(a.getId(), a.getFileName(), resolvedType, a.getDate(), size, hasThumb, txId);
	}

	private static String guessContentTypeFromFilename(String fileName) {
		if (fileName == null) return null;
		String lower = fileName.toLowerCase();
		if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
		if (lower.endsWith(".png")) return "image/png";
		if (lower.endsWith(".gif")) return "image/gif";
		if (lower.endsWith(".webp")) return "image/webp";
		if (lower.endsWith(".bmp")) return "image/bmp";
		if (lower.endsWith(".pdf")) return "application/pdf";
		return null;
	}
}
