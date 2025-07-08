package com.nklmthr.finance.personal.repository;

import com.nklmthr.finance.personal.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, String> {
    List<Category> findByParentIsNull();
    List<Category> findByParentId(String parentId);
	Optional<Category> findByName(String string);
}
