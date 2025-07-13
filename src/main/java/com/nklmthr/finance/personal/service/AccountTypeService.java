package com.nklmthr.finance.personal.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.nklmthr.finance.personal.model.AccountType;
import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.repository.AccountTypeRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class AccountTypeService {
	@Autowired
	private AppUserService appUserService;
    private final AccountTypeRepository accountTypeRepository;

    public AccountType create(AccountType accountType) {
    	AppUser appUser = appUserService.getCurrentUser();
        if (accountTypeRepository.existsByAppUserAndName(appUser, accountType.getName())) {
            throw new IllegalArgumentException("Account type with name already exists: " + accountType.getName());
        }
        accountType.setAppUser(appUser);
        return accountTypeRepository.save(accountType);
    }

    public List<AccountType> getAll() {
    	AppUser appUser = appUserService.getCurrentUser();
        return accountTypeRepository.findByAppUser(appUser);
    }

    public Optional<AccountType> getById(String id) {
    	AppUser appUser = appUserService.getCurrentUser();
        return accountTypeRepository.findByAppUserAndId(appUser, id);
    }

    public Optional<AccountType> getByName(String name) {
    	AppUser appUser = appUserService.getCurrentUser();
        return accountTypeRepository.findByAppUserAndName(appUser, name);
    }

    public AccountType update(String id, AccountType updated) {
    	AppUser appUser = appUserService.getCurrentUser();
        AccountType existing = accountTypeRepository.findByAppUserAndId(appUser, id)
                .orElseThrow(() -> new IllegalArgumentException("AccountType not found: " + id));
        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setClassification(updated.getClassification());
        existing.setAccountTypeBalance(updated.getAccountTypeBalance());
        existing.setAppUser(appUser); // Ensure the user is set
        return accountTypeRepository.save(existing);
    }

    public void delete(String id) {
    	AppUser appUser = appUserService.getCurrentUser();
        if (!accountTypeRepository.existsByAppUserAndId(appUser, id)) {
            throw new IllegalArgumentException("AccountType not found: " + id);
        }
        accountTypeRepository.deleteByAppUserAndId(appUser, id);
    }
}
