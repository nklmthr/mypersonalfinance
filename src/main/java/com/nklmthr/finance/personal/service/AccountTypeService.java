package com.nklmthr.finance.personal.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.nklmthr.finance.personal.dto.AccountTypeDTO;
import com.nklmthr.finance.personal.mapper.AccountTypeMapper;
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
	@Autowired
	private AccountTypeRepository accountTypeRepository;
	
	@Autowired
	private final AccountTypeMapper accountTypeMapper;

	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(AccountTypeService.class);

	public AccountType create(AccountType accountType) {
		AppUser appUser = appUserService.getCurrentUser();
		if (accountTypeRepository.existsByAppUserAndName(appUser, accountType.getName())) {
			throw new IllegalArgumentException("Account type with name already exists: " + accountType.getName());
		}
		accountType.setAppUser(appUser);
		logger.info(
				"Creating account type for user: " + appUser.getUsername() + " with name: " + accountType.getName());
		return accountTypeRepository.save(accountType);
	}

	public List<AccountTypeDTO> getAll() {
		AppUser appUser = appUserService.getCurrentUser();
		logger.info("Fetching all account types for user: " + appUser.getUsername());
		List<AccountType> accountTypes = accountTypeRepository.findByAppUser(appUser);
		return accountTypeMapper.toDTOList(accountTypes);
	}

	public Optional<AccountType> getById(String id) {
		AppUser appUser = appUserService.getCurrentUser();
		logger.info("Fetching account type with id: " + id + " for user: " + appUser.getUsername());
		return accountTypeRepository.findByAppUserAndId(appUser, id);
	}

	public Optional<AccountType> getByName(String name) {
		AppUser appUser = appUserService.getCurrentUser();
		logger.info("Fetching account type with name: " + name + " for user: " + appUser.getUsername());
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
		existing.setAppUser(appUser);
		logger.info("Updating account type with id: " + id + " for user: " + appUser.getUsername());
		return accountTypeRepository.save(existing);
	}

	public void delete(String id) {
		AppUser appUser = appUserService.getCurrentUser();
		if (!accountTypeRepository.existsByAppUserAndId(appUser, id)) {
			throw new IllegalArgumentException("AccountType not found: " + id);
		}
		logger.info("Deleting account type with id: " + id + " for user: " + appUser.getUsername());
		accountTypeRepository.deleteByAppUserAndId(appUser, id);
	}
}
