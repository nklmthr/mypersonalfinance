package com.nklmthr.finance.personal.controller;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.nklmthr.finance.personal.dto.AttachmentDTO;
import com.nklmthr.finance.personal.model.Attachment;
import com.nklmthr.finance.personal.service.AttachmentService;

@RestController
@RequestMapping("/api")
public class AttachmentController {

	private static final Logger logger = LoggerFactory.getLogger(AttachmentController.class);

	@Autowired
	private AttachmentService attachmentService;

	@GetMapping("/transactions/{transactionId}/attachments")
	public ResponseEntity<?> list(@PathVariable String transactionId) {
		try {
			List<AttachmentDTO> attachments = attachmentService.listForTransaction(transactionId);
			return ResponseEntity.ok(attachments);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
		}
	}

	@PostMapping("/transactions/{transactionId}/attachments")
	public ResponseEntity<?> upload(@PathVariable String transactionId,
			@RequestParam("file") MultipartFile file) {
		try {
			AttachmentDTO saved = attachmentService.upload(transactionId, file);
			return ResponseEntity.ok(saved);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
		} catch (IOException e) {
			logger.error("Failed to read uploaded file for transaction {}", transactionId, e);
			return ResponseEntity.internalServerError().body(Map.of("error", "Failed to read uploaded file"));
		}
	}

	@GetMapping("/attachments/{attachmentId}")
	public ResponseEntity<byte[]> download(@PathVariable String attachmentId,
			@RequestParam(value = "download", defaultValue = "false") boolean download) {
		try {
			Attachment attachment = attachmentService.getOwnedAttachment(attachmentId);
			byte[] body = attachment.getContent() != null ? attachment.getContent() : new byte[0];
			MediaType mediaType = resolveMediaType(attachment.getContentType(), attachment.getFileName());

			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(mediaType);
			headers.setContentLength(body.length);
			String dispositionType = download ? "attachment" : "inline";
			String filename = attachment.getFileName() != null ? attachment.getFileName() : "attachment";
			String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
			headers.set(HttpHeaders.CONTENT_DISPOSITION,
					dispositionType + "; filename=\"" + filename + "\"; filename*=UTF-8''" + encoded);
			return new ResponseEntity<>(body, headers, 200);
		} catch (IllegalArgumentException e) {
			logger.warn("Attachment download failed for id={}: {}", attachmentId, e.getMessage());
			return ResponseEntity.notFound().build();
		}
	}

	@GetMapping("/attachments/{attachmentId}/thumbnail")
	public ResponseEntity<?> thumbnail(@PathVariable String attachmentId) {
		try {
			Attachment attachment = attachmentService.getOwnedAttachment(attachmentId);
			byte[] thumb = attachment.getThumbnailData();
			if (thumb == null || thumb.length == 0) {
				return ResponseEntity.notFound().build();
			}
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.IMAGE_PNG);
			headers.setContentLength(thumb.length);
			return new ResponseEntity<>(thumb, headers, 200);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
		}
	}

	@DeleteMapping("/attachments/{attachmentId}")
	public ResponseEntity<?> delete(@PathVariable String attachmentId) {
		try {
			attachmentService.delete(attachmentId);
			return ResponseEntity.noContent().build();
		} catch (IllegalArgumentException e) {
			return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
		}
	}

	private MediaType resolveMediaType(String contentType, String fileName) {
		// 1. Honor a non-blank, non-octet-stream content type from the DB.
		if (contentType != null && !contentType.isBlank()
				&& !"application/octet-stream".equalsIgnoreCase(contentType.trim())) {
			try {
				return MediaType.parseMediaType(contentType);
			} catch (Exception ignored) {
				// fall through to filename-based inference
			}
		}
		// 2. Infer from filename extension — covers rows that were saved with a missing
		// or generic content type (e.g. browser drag-drop sometimes sends octet-stream).
		String fromExt = guessContentTypeFromFilename(fileName);
		if (fromExt != null) {
			try {
				return MediaType.parseMediaType(fromExt);
			} catch (Exception ignored) {
				// fall through
			}
		}
		// 3. Last-ditch: octet-stream (browser will download).
		return MediaType.APPLICATION_OCTET_STREAM;
	}

	private String guessContentTypeFromFilename(String fileName) {
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
