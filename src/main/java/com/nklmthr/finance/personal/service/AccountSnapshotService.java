package com.nklmthr.finance.personal.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.nklmthr.finance.personal.model.Account;
import com.nklmthr.finance.personal.model.AccountBalanceSnapshot;
import com.nklmthr.finance.personal.repository.AccountBalanceSnapshotRepository;
import com.nklmthr.finance.personal.repository.AccountRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountSnapshotService {
	private final AccountRepository accountRepository;
    private final AccountBalanceSnapshotRepository snapshotRepository;

    public void createSnapshotsForDate(LocalDate snapshotDate) {
        LocalDate twoWeeksAgo = snapshotDate.minusDays(14);
        List<Account> accounts = accountRepository.findAll();

        boolean anyExists = accounts.stream().anyMatch(account ->
            snapshotRepository.existsByAccountIdAndSnapshotDateAfter(account.getId(), twoWeeksAgo)
        );

        if (anyExists) {
            throw new IllegalStateException("Snapshots already exist for some accounts in the last 2 weeks.");
        }

        List<AccountBalanceSnapshot> snapshots = new ArrayList<>();
        for (Account account : accounts) {
            BigDecimal balance = account.getBalance() != null
                ? BigDecimal.valueOf(account.getBalance())
                : BigDecimal.ZERO;

            AccountBalanceSnapshot snapshot = AccountBalanceSnapshot.builder()
                .account(account)
                .balance(balance)
                .snapshotDate(snapshotDate)
                .build();

            snapshots.add(snapshot);
        }

        snapshotRepository.saveAll(snapshots);
    }


}
