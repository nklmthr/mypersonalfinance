package com.nklmthr.finance.personal.service;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.nklmthr.finance.personal.dto.AccountDTO;
import com.nklmthr.finance.personal.mapper.AccountMapper;
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

    @Autowired
    private AccountMapper accountMapper;

    private static final Logger logger = LoggerFactory.getLogger(AccountService.class);

    public List<AccountDTO> getAllAccounts() {
        AppUser appUser = appUserService.getCurrentUser();
        logger.info("Fetching all accounts for user: {}", appUser.getUsername());
        return accountRepository.findAllByAppUser(appUser, Sort.by("name").ascending())
                .stream()
                .map(accountMapper::toDTO)
                .collect(Collectors.toList());
    }

    public AccountDTO findById(String id) {
        AppUser appUser = appUserService.getCurrentUser();
        logger.info("Finding account by id: {} for user: {}", id, appUser.getUsername());
        return accountMapper.toDTO(findByAppUserAndId(id, appUser));
    }

    private Account findByAppUserAndId(String id, AppUser appUser) {
        logger.debug("Fetching account with id: {} for user: {}", id, appUser.getUsername());
        return accountRepository.findByAppUserAndId(appUser, id)
                .orElseThrow(() -> new RuntimeException("No access to this account or Account not found with id: " + id));
    }

    public AccountDTO createAccount(AccountDTO accountDTO) {
        AppUser appUser = appUserService.getCurrentUser();
        Account account = accountMapper.toEntity(accountDTO);

        if (institutionRepository.findByAppUserAndId(appUser, account.getInstitution().getId()).isEmpty()) {
            logger.error("Institution not found for user: {}", appUser.getUsername());
            throw new RuntimeException("Institution not found");
        }
        if (!accountTypeRepository.existsByAppUserAndId(appUser, account.getAccountType().getId())) {
            logger.error("AccountType not found for user: {}", appUser.getUsername());
            throw new RuntimeException("AccountType not found");
        }
        account.setAppUser(appUser);
        logger.info("Creating account for user: {} with name: {}", appUser.getUsername(), account.getName());
        return accountMapper.toDTO(accountRepository.save(account));
    }

    public AccountDTO updateAccount(String id, AccountDTO updatedAccountDTO) {
        AppUser appUser = appUserService.getCurrentUser();
        Account account = findByAppUserAndId(id, appUser);

        Account updatedAccount = accountMapper.toEntity(updatedAccountDTO);
        account.setName(updatedAccount.getName());
        account.setBalance(updatedAccount.getBalance());
        account.setAccountNumber(updatedAccount.getAccountNumber());
        account.setAccountKeywords(updatedAccount.getAccountKeywords());
        account.setAccountAliases(updatedAccount.getAccountAliases());
        
        if (updatedAccount.getInstitution() != null) {
            account.setInstitution(updatedAccount.getInstitution());
        }
        if (updatedAccount.getAccountType() != null) {
            account.setAccountType(updatedAccount.getAccountType());
        }
        account.setAppUser(appUser);
        logger.info("Updating account with id: {} for user: {}", id, appUser.getUsername());
        return accountMapper.toDTO(accountRepository.save(account));
    }

    public void deleteAccount(String id) {
        AppUser appUser = appUserService.getCurrentUser();
        if (!acountTransactionRepository.findByAppUserAndAccountId(appUser, id).isEmpty()) {
            logger.error("Cannot delete account with existing transactions for user: {}", appUser.getUsername());
            throw new IllegalStateException("Cannot delete account with existing transactions.");
        }
        logger.info("Deleting account with id: {} for user: {}", id, appUser.getUsername());
        accountRepository.deleteById(id);
    }

    public List<AccountDTO> getFilteredAccounts(String accountTypeId, String institutionId) {
        AppUser appUser = appUserService.getCurrentUser();
        List<Account> accounts;

        if (accountTypeId != null && institutionId != null) {
            logger.info("Fetching accounts for user: {} with accountTypeId: {} and institutionId: {}",
                    appUser.getUsername(), accountTypeId, institutionId);
            accounts = accountRepository.findByAppUserAndAccountTypeIdAndInstitutionId(appUser, accountTypeId, institutionId);
        } else if (accountTypeId != null) {
            logger.info("Fetching accounts for user: {} with accountTypeId: {}", appUser.getUsername(), accountTypeId);
            accounts = accountRepository.findByAppUserAndAccountTypeId(appUser, accountTypeId);
        } else if (institutionId != null) {
            logger.info("Fetching accounts for user: {} with institutionId: {}", appUser.getUsername(), institutionId);
            accounts = accountRepository.findByAppUserAndInstitutionId(appUser, institutionId);
        } else {
            logger.info("Fetching all accounts for user: {}", appUser.getUsername());
            accounts = accountRepository.findAllByAppUser(appUser, Sort.by("name").ascending());
        }

        return accounts.stream().map(accountMapper::toDTO).collect(Collectors.toList());
    }

    public AccountDTO getAccountByName(String accountName) {
        AppUser appUser = appUserService.getCurrentUser();
        logger.info("Fetching account by name: {} for user: {}", accountName, appUser.getUsername());
        return accountMapper.toDTO(getAccountByName(accountName, appUser));
    }

    public Account getAccountByName(String accountName, AppUser appUser) {
        logger.debug("Fetching account by name: {} for user: {}", accountName, appUser.getUsername());
        return accountRepository.findByAppUserAndName(appUser, accountName)
                .orElseThrow(() -> new RuntimeException("Account not found with name: " + accountName));
    }

    public List<AccountDTO> getAllAccounts(AppUser appUser) {
    			logger.info("Fetching all accounts for user: {}", appUser.getUsername());
		return accountMapper.toDTOList(accountRepository.findAllByAppUser(appUser, Sort.by("name").ascending()));
    }
    
    public void save(AccountDTO accountDTO) {
        AppUser appUser = appUserService.getCurrentUser();
        Account account = accountMapper.toEntity(accountDTO);
        account.setAppUser(appUser);
        logger.info("Saving account for user: {} with name: {}", appUser.getUsername(), account.getName());
        accountRepository.save(account);
    }
}
