package com.nklmthr.finance.personal.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nklmthr.finance.personal.model.AccountTransaction;

@Repository
public interface AccountTransactionRepository extends JpaRepository<AccountTransaction, String> {
    List<AccountTransaction> findByParentIsNull(); // root transactions
    List<AccountTransaction> findByParentId(String parentId);
    List<AccountTransaction> findByAccountId(String accountId);
    
    @Query("SELECT t FROM AccountTransaction t WHERE FUNCTION('MONTH', t.date) = :month AND FUNCTION('YEAR', t.date) = :year")
    List<AccountTransaction> findByMonthAndYear(@Param("month") int month, @Param("year") int year);
}
