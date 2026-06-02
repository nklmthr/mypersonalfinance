package com.nklmthr.finance.personal.service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.nklmthr.finance.personal.dto.AccountTypeDTO;
import com.nklmthr.finance.personal.mapper.AccountTypeMapper;
import com.nklmthr.finance.personal.model.Account;
import com.nklmthr.finance.personal.model.AccountType;
import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.repository.AccountRepository;
import com.nklmthr.finance.personal.repository.AccountTypeRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class AccountTypeService {

    private final AppUserService appUserService;
    private final AccountTypeRepository accountTypeRepository;
    private final AccountRepository accountRepository;
    private final AccountTypeMapper accountTypeMapper;

    private static final Logger logger = LoggerFactory.getLogger(AccountTypeService.class);

    public AccountTypeDTO create(AccountTypeDTO dto) {
        AppUser appUser = appUserService.getCurrentUser();

        if (accountTypeRepository.existsByAppUserAndName(appUser, dto.name())) {
            throw new IllegalArgumentException("Account type with name already exists: " + dto.name());
        }

        AccountType entity = accountTypeMapper.toEntity(dto);
        entity.setAppUser(appUser);

        logger.info("Creating account type for user: " + appUser.getUsername() + " with name: " + dto.name());
        AccountType saved = accountTypeRepository.save(entity);
        // A brand-new type has no accounts yet, so the derived balance is 0.
        // We still call the helper for symmetry with update() and to make this
        // robust if create() is ever invoked against an existing id.
        saved.setAccountTypeBalance(computeBalanceFor(appUser, saved.getId()));
        return accountTypeMapper.toDTO(saved);
    }

    public List<AccountTypeDTO> getAll() {
        AppUser appUser = appUserService.getCurrentUser();
        logger.info("Fetching all account types for user: " + appUser.getUsername());
        List<AccountType> accountTypes = accountTypeRepository.findByAppUser(appUser);

        if (accountTypes.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, BigDecimal> totalsByType = totalsByAccountTypeId(appUser);
        accountTypes.forEach(type -> type.setAccountTypeBalance(totalsByType.getOrDefault(type.getId(), BigDecimal.ZERO)));

        return accountTypeMapper.toDTOList(accountTypes);
    }

    /**
     * Returns the sum of {@link Account#getBalance()} grouped by account type id
     * for the given user. The balance on {@link AccountType} is purely derived
     * from these account rows — there is no persisted balance column, so this
     * is the single source of truth for the value shown to the user.
     */
    private Map<String, BigDecimal> totalsByAccountTypeId(AppUser appUser) {
        List<Account> accounts = accountRepository.findAllByAppUser(appUser, Sort.unsorted());
        return accounts.stream()
                .filter(account -> account.getAccountType() != null && account.getAccountType().getId() != null)
                .filter(account -> Objects.nonNull(account.getBalance()))
                .collect(Collectors.groupingBy(account -> account.getAccountType().getId(),
                        Collectors.mapping(Account::getBalance,
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));
    }

    private BigDecimal computeBalanceFor(AppUser appUser, String accountTypeId) {
        if (accountTypeId == null) return BigDecimal.ZERO;
        return totalsByAccountTypeId(appUser).getOrDefault(accountTypeId, BigDecimal.ZERO);
    }

    public Optional<AccountTypeDTO> getById(String id) {
        AppUser appUser = appUserService.getCurrentUser();
        logger.info("Fetching account type with id: " + id + " for user: " + appUser.getUsername());
        return accountTypeRepository.findByAppUserAndId(appUser, id)
                .map(accountTypeMapper::toDTO);
    }

    public Optional<AccountTypeDTO> getByName(String name) {
        AppUser appUser = appUserService.getCurrentUser();
        logger.info("Fetching account type with name: " + name + " for user: " + appUser.getUsername());
        return accountTypeRepository.findByAppUserAndName(appUser, name)
                .map(accountTypeMapper::toDTO);
    }

    public AccountTypeDTO update(String id, AccountTypeDTO updatedDto) {
        AppUser appUser = appUserService.getCurrentUser();
        AccountType existing = accountTypeRepository.findByAppUserAndId(appUser, id)
                .orElseThrow(() -> new IllegalArgumentException("AccountType not found: " + id));

        // Update entity fields from DTO. Note: accountTypeBalance is intentionally
        // NOT taken from the request — it is a @Transient derived field on the
        // entity computed from the sum of Account.balance for accounts of this
        // type. Previously this method called setAccountTypeBalance(...) here,
        // which was a dead write that nonetheless made the immediate response
        // echo whatever value the client posted, then "magically reverted" on the
        // next GET. The frontend now also stops sending the field, but we
        // explicitly ignore it on the server too as a defence-in-depth.
        existing.setName(updatedDto.name());
        existing.setDescription(updatedDto.description());
        existing.setClassification(updatedDto.classification());
        existing.setAppUser(appUser);

        logger.info("Updating account type with id: " + id + " for user: " + appUser.getUsername());

        AccountType saved = accountTypeRepository.save(existing);
        // Recompute the derived balance so the PUT response matches what a
        // subsequent GET would return — keeps the UI consistent without an
        // extra refetch round-trip.
        saved.setAccountTypeBalance(computeBalanceFor(appUser, saved.getId()));
        return accountTypeMapper.toDTO(saved);
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
