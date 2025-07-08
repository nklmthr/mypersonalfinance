package com.nklmthr.finance.personal.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.nklmthr.finance.personal.model.Account;
import com.nklmthr.finance.personal.repository.AccountRepository;
import com.nklmthr.finance.personal.repository.AccountTransactionRepository;
import com.nklmthr.finance.personal.repository.AccountTypeRepository;
import com.nklmthr.finance.personal.repository.InstitutionRepository;

@Service
public class AccountService {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private InstitutionRepository institutionRepository;

    @Autowired
    private AccountTypeRepository accountTypeRepository;
    
    @Autowired
    private AccountTransactionRepository acountTransactionRepository;

    public List<Account> getAllAccounts() {
        return accountRepository.findAll();
    }

    public Account getAccount(String id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Account not found with id: " + id));
    }

    public Account createAccount(Account account) {
        if (!institutionRepository.existsById(account.getInstitution().getId())) {
            throw new RuntimeException("Institution not found");
        }
        if (!accountTypeRepository.existsById(account.getAccountType().getId())) {
            throw new RuntimeException("AccountType not found");
        }
        return accountRepository.save(account);
    }

    public Account updateAccount(String id, Account updatedAccount) {
        Account account = getAccount(id);

        account.setName(updatedAccount.getName());
        account.setDescription(updatedAccount.getDescription());
        account.setBalance(updatedAccount.getBalance());

        if (updatedAccount.getInstitution() != null) {
            account.setInstitution(updatedAccount.getInstitution());
        }
        if (updatedAccount.getAccountType() != null) {
            account.setAccountType(updatedAccount.getAccountType());
        }

        return accountRepository.save(account);
    }

    public void deleteAccount(String id) {
    	if (!acountTransactionRepository.findByAccountId(id).isEmpty()) {
            throw new IllegalStateException("Cannot delete account with existing transactions.");
        }
        accountRepository.deleteById(id);
    }
    
    public List<Account> getFilteredAccounts(Long accountTypeId, Long institutionId) {
        if (accountTypeId != null && institutionId != null) {
            return accountRepository.findByAccountTypeIdAndInstitutionId(accountTypeId, institutionId);
        } else if (accountTypeId != null) {
            return accountRepository.findByAccountTypeId(accountTypeId);
        } else if (institutionId != null) {
            return accountRepository.findByInstitutionId(institutionId);
        } else {
            return accountRepository.findAll();
        }
    }

	public Account getAccountByName(String string) {
		return accountRepository.findByName(string)
				.orElseThrow(() -> new RuntimeException("Account not found with name: " + string));
	}

}
