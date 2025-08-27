package com.nklmthr.finance.personal.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.model.Category;

public interface CategoryRepository extends JpaRepository<Category, String> {

	List<Category> findByAppUserAndParent(AppUser appUser, String parentId);

	Optional<Category> findByAppUserAndName(AppUser appUser, String name);

	List<Category> findByAppUser(AppUser appUser, Sort sort);

	List<Category> findByAppUser(AppUser appUser);

	void deleteByAppUserAndId(AppUser appUser, String id);

	Optional<Category> findByAppUserAndId(AppUser appUser, String id);

}
