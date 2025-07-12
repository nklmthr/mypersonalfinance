package com.nklmthr.finance.personal.service;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.nklmthr.finance.personal.model.AccountTransaction;
import com.nklmthr.finance.personal.repository.AccountTransactionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountTransactionService {

    private final AccountTransactionRepository accountTransactionRepository;

    public Page<AccountTransaction> getRootTransactions(Pageable pageable) {
        return accountTransactionRepository.findAllWithGraph(pageable);
    }

    public Optional<AccountTransaction> getById(String id) {
        return accountTransactionRepository.findById(id);
    }

    
    public List<AccountTransaction> getChildren(String parentId) {
        return accountTransactionRepository.findByParentId(parentId);
    }

    public AccountTransaction save(AccountTransaction transaction) {
        return accountTransactionRepository.save(transaction);
    }

    public void delete(String id) {
        accountTransactionRepository.deleteById(id);
    }
    
    public boolean isTransactionAlreadyPresent(AccountTransaction newTransaction) {
        List<AccountTransaction> existingAccTxnList = accountTransactionRepository.findBySourceThreadId(newTransaction.getSourceThreadId());

        if (existingAccTxnList.isEmpty()) {
            return false;
        }
        // Check if the new transaction matches any existing transaction
        for (AccountTransaction existingTxn : existingAccTxnList) {
			if (existingTxn.getDate().equals(newTransaction.getDate())
					&& existingTxn.getAmount().compareTo(newTransaction.getAmount()) == 0
					&& existingTxn.getDescription().equalsIgnoreCase(newTransaction.getDescription())
					&& existingTxn.getType().equals(newTransaction.getType())) {
				return true; // Transaction already exists
			}
			
		}
        // Check if the new transaction matches any existing transaction by sourceMessageId
        for (AccountTransaction existingTxn : existingAccTxnList) {
			if (existingTxn.getSourceId().equals(newTransaction.getSourceId())) {
				return true; // Transaction already exists by sourceMessageId
			}
		}
		return false; // No match found, transaction is new
    }
}
