package com.nklmthr.finance.personal.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nklmthr.finance.personal.model.PredictedTransaction;
import com.nklmthr.finance.personal.model.PredictionActualTxnMapping;

@Repository
public interface PredictionActualTxnMappingRepository extends JpaRepository<PredictionActualTxnMapping, String> {

	@EntityGraph(attributePaths = {
		"actualTransaction",
		"actualTransaction.account",
		"actualTransaction.account.accountType",
		"actualTransaction.account.institution",
		"actualTransaction.category",
		"actualTransaction.category.parent",
		"actualTransaction.transactionLabels",
		"actualTransaction.transactionLabels.label"
	})
	List<PredictionActualTxnMapping> findByPredictedTransaction(PredictedTransaction predictedTransaction);
	
	List<PredictionActualTxnMapping> findByActualTransaction(com.nklmthr.finance.personal.model.AccountTransaction actualTransaction);
	
	void deleteByPredictedTransaction(PredictedTransaction predictedTransaction);
	
	void deleteByActualTransaction(com.nklmthr.finance.personal.model.AccountTransaction actualTransaction);
}

