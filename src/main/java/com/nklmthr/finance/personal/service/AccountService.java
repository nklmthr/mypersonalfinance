package com.nklmthr.finance.personal.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.nklmthr.finance.personal.model.Account;
import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.repository.AccountRepository;
import com.nklmthr.finance.personal.repository.AccountTransactionRepository;
import com.nklmthr.finance.personal.repository.AccountTypeRepository;
import com.nklmthr.finance.personal.repository.InstitutionRepository;

@Service
public class AccountService {
	@Autowired
	private AppUserService appUserService;

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private InstitutionRepository institutionRepository;

	@Autowired
	private AccountTypeRepository accountTypeRepository;

	@Autowired
	private AccountTransactionRepository acountTransactionRepository;

	public List<Account> getAllAccounts() {
		AppUser appUser = appUserService.getCurrentUser();
		return accountRepository.findAllByAppUser(appUser);
	}

	public Account getAccount(String id) {
		AppUser appUser = appUserService.getCurrentUser();
		return accountRepository.findByAppUserAndId(appUser, id).orElseThrow(
				() -> new RuntimeException("No access to this account or Account not found with id: " + id));
	}

	public Account createAccount(Account account) {
		AppUser appUser = appUserService.getCurrentUser();
		if (institutionRepository.findByAppUserAndId(appUser, account.getInstitution().getId()).isEmpty()) {
			throw new RuntimeException("Institution not found");
		}
		if (!accountTypeRepository.existsByAppUserAndId(appUser, account.getAccountType().getId())) {
			throw new RuntimeException("AccountType not found");
		}
		account.setAppUser(appUser);
		return accountRepository.save(account);
	}

	public Account updateAccount(String id, Account updatedAccount) {
		AppUser appUser = appUserService.getCurrentUser();
		Account account = getAccount(id);

		account.setName(updatedAccount.getName());
		account.setBalance(updatedAccount.getBalance());

		if (updatedAccount.getInstitution() != null) {
			account.setInstitution(updatedAccount.getInstitution());
		}
		if (updatedAccount.getAccountType() != null) {
			account.setAccountType(updatedAccount.getAccountType());
		}
		account.setAppUser(appUser);
		return accountRepository.save(account);
	}

	public void deleteAccount(String id) {
		AppUser appUser = appUserService.getCurrentUser();
		if (!acountTransactionRepository.findByAppUserAndAccountId(appUser, id).isEmpty()) {
			throw new IllegalStateException("Cannot delete account with existing transactions.");
		}
		accountRepository.deleteById(id);
	}

	public List<Account> getFilteredAccounts(String accountTypeId, String institutionId) {
		AppUser appUser = appUserService.getCurrentUser();
		if (accountTypeId != null && institutionId != null) {
			return accountRepository.findByAppUserAndAccountTypeIdAndInstitutionId(appUser, accountTypeId, institutionId);
		} else if (accountTypeId != null) {
			return accountRepository.findByAppUserAndAccountTypeId(appUser, accountTypeId);
		} else if (institutionId != null) {
			return accountRepository.findByAppUserAndInstitutionId(appUser, institutionId);
		} else {
			return accountRepository.findAllByAppUser(appUser);
		}
	}

	public Account getAccountByName(String accountName) {
		AppUser appUser = appUserService.getCurrentUser();
		return getAccountByName(accountName, appUser);
	}
	
	public Account getAccountByName(String accountName, AppUser appUser) {
		return accountRepository.findByAppUserAndName(appUser, accountName)
				.orElseThrow(() -> new RuntimeException("Account not found with name: " + accountName));
	}

}
