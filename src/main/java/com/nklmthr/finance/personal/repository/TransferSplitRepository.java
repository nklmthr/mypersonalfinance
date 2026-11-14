package com.nklmthr.finance.personal.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nklmthr.finance.personal.model.TransferSplit;

@Repository
public interface TransferSplitRepository extends JpaRepository<TransferSplit, String> {

    List<TransferSplit> findBySourceTransactionId(String sourceTransactionId);

    List<TransferSplit> findByDestTransactionId(String destTransactionId);

    void deleteAllBySourceTransactionId(String sourceTransactionId);

    void deleteByDestTransactionId(String destTransactionId);

    boolean existsBySourceTransactionId(String sourceTransactionId);

    /** Returns [sourceTransactionId, count] pairs for the given IDs. */
    @Query("SELECT ts.sourceTransactionId, COUNT(ts) FROM TransferSplit ts WHERE ts.sourceTransactionId IN :ids GROUP BY ts.sourceTransactionId")
    List<Object[]> countBySourceTransactionIdIn(@Param("ids") List<String> ids);

    /** Returns dest transaction IDs from the given set that appear as destinations in any split. */
    @Query("SELECT ts.destTransactionId FROM TransferSplit ts WHERE ts.destTransactionId IN :ids")
    List<String> findDestTransactionIdIn(@Param("ids") List<String> ids);

    /** Returns true if the given ID is a dest in any split. */
    boolean existsByDestTransactionId(String destTransactionId);
}
