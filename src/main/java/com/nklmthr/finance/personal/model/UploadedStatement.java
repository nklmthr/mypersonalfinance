package com.nklmthr.finance.personal.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.UuidGenerator;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UploadedStatement {
	@Id
	@UuidGenerator
	@Column
	private String id;

	@Column(nullable = false, length = 255)
	private String filename;

	@Column(length = 100)
	private String uploadedBy;

	@Column
	private LocalDateTime uploadedAt;

	@Column
	@Enumerated(EnumType.STRING)
	private Status status;

	@Lob
	@Column(columnDefinition = "TEXT")
	private String content;
	
	@Lob
	@Column(columnDefinition = "MEDIUMBLOB")
	private byte[] binaryContent;

	@ManyToOne
	@JoinColumn(name = "account_id")
	private Account account;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "app_user_id")
	@JsonIgnore
	private AppUser appUser;

	public enum Status {
		UPLOADED, PROCESSED, FAILED
	}

}
