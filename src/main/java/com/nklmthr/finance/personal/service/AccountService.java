package com.nklmthr.finance.personal.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
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
	
	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(AccountService.class);

	public List<Account> getAllAccounts() {
		AppUser appUser = appUserService.getCurrentUser();
		logger.info("Fetching all accounts for user: " + appUser.getUsername());
		return accountRepository.findAllByAppUser(appUser, Sort.by("name").ascending());
	}

	public Account getAccount(String id) {
		AppUser appUser = appUserService.getCurrentUser();
		logger.info("Fetching account with id: " + id + " for user: " + appUser.getUsername());
		return accountRepository.findByAppUserAndId(appUser, id).orElseThrow(
				() -> new RuntimeException("No access to this account or Account not found with id: " + id));
	}

	public Account createAccount(Account account) {
		AppUser appUser = appUserService.getCurrentUser();
		if (institutionRepository.findByAppUserAndId(appUser, account.getInstitution().getId()).isEmpty()) {
			logger.error("Institution not found for user: " + appUser.getUsername());
			throw new RuntimeException("Institution not found");
		}
		if (!accountTypeRepository.existsByAppUserAndId(appUser, account.getAccountType().getId())) {
			logger.error("AccountType not found for user: " + appUser.getUsername());
			throw new RuntimeException("AccountType not found");
		}
		account.setAppUser(appUser);
		logger.info("Creating account for user: " + appUser.getUsername() + " with name: " + account.getName());
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
		logger.info("Updating account with id: " + id + " for user: " + appUser.getUsername());
		return accountRepository.save(account);
	}

	public void deleteAccount(String id) {
		AppUser appUser = appUserService.getCurrentUser();
		if (!acountTransactionRepository.findByAppUserAndAccountId(appUser, id).isEmpty()) {
			logger.error("Cannot delete account with existing transactions for user: " + appUser.getUsername());
			throw new IllegalStateException("Cannot delete account with existing transactions.");
		}
		logger.info("Deleting account with id: " + id + " for user: " + appUser.getUsername());
		accountRepository.deleteById(id);
	}

	public List<Account> getFilteredAccounts(String accountTypeId, String institutionId) {
		AppUser appUser = appUserService.getCurrentUser();
		if (accountTypeId != null && institutionId != null) {
			logger.info("Fetching accounts for user: " + appUser.getUsername() + " with accountTypeId: " + accountTypeId + " and institutionId: " + institutionId);
			return accountRepository.findByAppUserAndAccountTypeIdAndInstitutionId(appUser, accountTypeId, institutionId);
		} else if (accountTypeId != null) {
			logger.info("Fetching accounts for user: " + appUser.getUsername() + " with accountTypeId: " + accountTypeId);
			return accountRepository.findByAppUserAndAccountTypeId(appUser, accountTypeId);
		} else if (institutionId != null) {
			logger.info("Fetching accounts for user: " + appUser.getUsername() + " with institutionId: " + institutionId);
			return accountRepository.findByAppUserAndInstitutionId(appUser, institutionId);
		} else {
			logger.info("Fetching all accounts for user: " + appUser.getUsername());
			return accountRepository.findAllByAppUser(appUser, Sort.by("name").ascending());
		}
	}

	public Account getAccountByName(String accountName) {
		AppUser appUser = appUserService.getCurrentUser();
		logger.info("Fetching account by name: " + accountName + " for user: " + appUser.getUsername());
		return getAccountByName(accountName, appUser);
	}
	
	public Account getAccountByName(String accountName, AppUser appUser) {
		logger.info("Fetching account by name: " + accountName + " for user: " + appUser.getUsername());
		return accountRepository.findByAppUserAndName(appUser, accountName)
				.orElseThrow(() -> new RuntimeException("Account not found with name: " + accountName));
	}

}
