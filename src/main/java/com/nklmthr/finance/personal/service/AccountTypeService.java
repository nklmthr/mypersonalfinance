package com.nklmthr.finance.personal.service;

import com.nklmthr.finance.personal.model.AccountType;
import com.nklmthr.finance.personal.repository.AccountTypeRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class AccountTypeService {

    private final AccountTypeRepository accountTypeRepository;

    public AccountType create(AccountType accountType) {
        if (accountTypeRepository.existsByName(accountType.getName())) {
            throw new IllegalArgumentException("Account type with name already exists: " + accountType.getName());
        }
        return accountTypeRepository.save(accountType);
    }

    public List<AccountType> getAll() {
        return accountTypeRepository.findAll();
    }

    public Optional<AccountType> getById(Long id) {
        return accountTypeRepository.findById(id);
    }

    public Optional<AccountType> getByName(String name) {
        return accountTypeRepository.findByName(name);
    }

    public AccountType update(Long id, AccountType updated) {
        AccountType existing = accountTypeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("AccountType not found: " + id));
        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setClassification(updated.getClassification());
        existing.setAccountTypeBalance(updated.getAccountTypeBalance());
        return accountTypeRepository.save(existing);
    }

    public void delete(Long id) {
        if (!accountTypeRepository.existsById(id)) {
            throw new IllegalArgumentException("AccountType not found: " + id);
        }
        accountTypeRepository.deleteById(id);
    }
}
