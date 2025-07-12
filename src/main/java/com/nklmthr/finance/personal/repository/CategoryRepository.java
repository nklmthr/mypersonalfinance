package com.nklmthr.finance.personal.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.nklmthr.finance.personal.model.Category;
import com.nklmthr.finance.personal.model.FlatCategory;

public interface CategoryRepository extends JpaRepository<Category, String> {
    List<Category> findByParentId(String parentId);
	Optional<Category> findByName(String string);
	List<FlatCategory> findAllProjectedBy(Sort sort);
	@Query("SELECT c FROM Category c")
	List<Category> findAllFlat();
	
	
	
}
