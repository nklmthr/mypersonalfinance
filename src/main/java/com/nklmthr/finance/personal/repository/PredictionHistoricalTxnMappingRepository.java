package com.nklmthr.finance.personal.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nklmthr.finance.personal.model.PredictedTransaction;
import com.nklmthr.finance.personal.model.PredictionHistoricalTxnMapping;

@Repository
public interface PredictionHistoricalTxnMappingRepository extends JpaRepository<PredictionHistoricalTxnMapping, String> {

	@EntityGraph(attributePaths = {
		"historicalTransaction",
		"historicalTransaction.account",
		"historicalTransaction.account.accountType",
		"historicalTransaction.account.institution",
		"historicalTransaction.category",
		"historicalTransaction.category.parent",
		"historicalTransaction.transactionLabels",
		"historicalTransaction.transactionLabels.label"
	})
	List<PredictionHistoricalTxnMapping> findByPredictedTransaction(PredictedTransaction predictedTransaction);
	
	void deleteByPredictedTransaction(PredictedTransaction predictedTransaction);
}

