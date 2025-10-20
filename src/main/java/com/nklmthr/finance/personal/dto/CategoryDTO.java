package com.nklmthr.finance.personal.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.nklmthr.finance.personal.model.Category;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoryDTO {
	public CategoryDTO(Category category) {
		this.id = category.getId();
		this.name = category.getName();
		this.parentId = category.getParent() != null ? category.getParent() : null;
		this.systemCategory = category.isSystemCategory();
	}

	private String id;
	private String name;
    @JsonProperty("parentId")
    @JsonAlias({"parent"})
    private String parentId;
	private boolean systemCategory;
	private List<CategoryDTO> children = new ArrayList<>();

}
