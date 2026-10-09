package com.nklmthr.finance.personal.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

	boolean existsByPredictedTransactionAndActualTransaction(PredictedTransaction predictedTransaction, com.nklmthr.finance.personal.model.AccountTransaction actualTransaction);

	void deleteByActualTransaction(com.nklmthr.finance.personal.model.AccountTransaction actualTransaction);

	@Modifying
	@Query("DELETE FROM PredictionActualTxnMapping m WHERE m.predictedTransaction IN :predictions")
	void deleteByPredictedTransactionIn(@Param("predictions") List<PredictedTransaction> predictions);

	@Query("SELECT m.actualTransaction.id FROM PredictionActualTxnMapping m WHERE m.predictedTransaction.appUser = :appUser AND m.predictedTransaction.predictionMonth = :month")
	List<String> findMappedTransactionIdsByAppUserAndMonth(@Param("appUser") com.nklmthr.finance.personal.model.AppUser appUser, @Param("month") String month);
}

