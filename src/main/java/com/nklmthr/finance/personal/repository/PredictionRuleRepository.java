package com.nklmthr.finance.personal.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.model.Category;
import com.nklmthr.finance.personal.model.PredictionRule;

@Repository
public interface PredictionRuleRepository extends JpaRepository<PredictionRule, String> {

	List<PredictionRule> findByAppUser(AppUser appUser);
	
	List<PredictionRule> findByAppUserAndEnabledTrue(AppUser appUser);
	
	Optional<PredictionRule> findByAppUserAndCategory(AppUser appUser, Category category);
	
	List<PredictionRule> findByCategory(Category category);
	
	boolean existsByAppUserAndCategory(AppUser appUser, Category category);
}

