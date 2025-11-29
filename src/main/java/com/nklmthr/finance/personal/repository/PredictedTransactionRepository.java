package com.nklmthr.finance.personal.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.model.Category;
import com.nklmthr.finance.personal.model.PredictedTransaction;
import com.nklmthr.finance.personal.model.PredictionRule;

@Repository
public interface PredictedTransactionRepository extends JpaRepository<PredictedTransaction, String> {

	List<PredictedTransaction> findByAppUser(AppUser appUser);
	
	List<PredictedTransaction> findByAppUserAndPredictionMonth(AppUser appUser, String predictionMonth);
	
	List<PredictedTransaction> findByAppUserAndPredictionMonthAndVisibleTrue(AppUser appUser, String predictionMonth);
	
	List<PredictedTransaction> findByAppUserAndCategoryAndPredictionMonth(AppUser appUser, Category category, String predictionMonth);
	
	List<PredictedTransaction> findByPredictionRule(PredictionRule predictionRule);
	
	Optional<PredictedTransaction> findByPredictionRuleAndPredictionMonth(PredictionRule predictionRule, String predictionMonth);
	
	void deleteByPredictionRule(PredictionRule predictionRule);
	
	@Query("SELECT pt FROM PredictedTransaction pt WHERE pt.appUser = :appUser " +
	       "AND pt.predictionMonth >= :startMonth AND pt.predictionMonth <= :endMonth " +
	       "AND pt.visible = true")
	List<PredictedTransaction> findByAppUserAndMonthRange(@Param("appUser") AppUser appUser,
	                                                       @Param("startMonth") String startMonth,
	                                                       @Param("endMonth") String endMonth);
}

