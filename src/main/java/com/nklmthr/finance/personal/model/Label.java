package com.nklmthr.finance.personal.model;

import org.hibernate.annotations.UuidGenerator;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "labels", uniqueConstraints = @UniqueConstraint(columnNames = {"app_user_id", "name"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Label {

	@Id
	@UuidGenerator
	@Column
	private String id;

	@Column(nullable = false, length = 255)
	private String name;

	@ManyToOne(optional = false)
	@JsonIgnore
	private AppUser appUser;
}

