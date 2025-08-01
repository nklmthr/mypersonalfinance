package com.nklmthr.finance.personal.model;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Date;

import javax.imageio.ImageIO;

import org.apache.commons.codec.binary.Base64;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.hibernate.annotations.UuidGenerator;
import org.imgscalr.Scalr;
import org.springframework.format.annotation.DateTimeFormat;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "attachments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Attachment {
	@Id
	@UuidGenerator
	@Column
	private String id;

	@Column
	@DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
	private Date date;

	@Column
	private String fileName;

	@Column(length = 100)
	private String contentType;

	@Lob
	@Column(columnDefinition = "MEDIUMBLOB")
	private byte[] content;

	@Lob
	@Column(columnDefinition = "MEDIUMBLOB")
	private byte[] thumbnailData;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "account_transaction_id", referencedColumnName = "id", nullable = false)
	@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
	private AccountTransaction accountTransaction;

	public String getFileType() {
		return getFileName().substring(getFileName().lastIndexOf(".") + 1, getFileName().length());
	}

	public String getBase64EncodedImage() {
		return Base64.encodeBase64String(content);
	}

	public static byte[] generateThumbnail(String contentType, byte[] contentData) throws IOException {
		if (contentType.startsWith("image/")) {
			BufferedImage img = ImageIO.read(new ByteArrayInputStream(contentData));
			BufferedImage scaledImg = Scalr.resize(img, Scalr.Method.QUALITY, 200);
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ImageIO.write(scaledImg, "png", baos);
			return baos.toByteArray();
		} else if (contentType.equals("application/pdf")) {
			try (PDDocument document = Loader.loadPDF(contentData)) {
				PDFRenderer pdfRenderer = new PDFRenderer(document);
				BufferedImage pageImage = pdfRenderer.renderImageWithDPI(0, 100); // First page
				BufferedImage scaledImg = Scalr.resize(pageImage, Scalr.Method.QUALITY, 200);
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				ImageIO.write(scaledImg, "png", baos);
				return baos.toByteArray();
			}
		}
		return null;
	}

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JsonIgnore
	private AppUser appUser;

}
