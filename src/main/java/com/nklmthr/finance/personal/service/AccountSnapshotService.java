package com.nklmthr.finance.personal.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.nklmthr.finance.personal.model.Account;
import com.nklmthr.finance.personal.model.AccountBalanceSnapshot;
import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.repository.AccountBalanceSnapshotRepository;
import com.nklmthr.finance.personal.repository.AccountRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountSnapshotService {
	private final AccountRepository accountRepository;
	private final AccountBalanceSnapshotRepository snapshotRepository;
	@Autowired
	private AppUserService appUserService;

	public void createSnapshotsForDate(LocalDateTime snapshotDate) {
		AppUser appUser = appUserService.getCurrentUser();
		LocalDateTime twoWeeksAgo = snapshotDate.minusDays(14);
		List<Account> accounts = accountRepository.findAll();

		boolean anyExists = accounts.stream().anyMatch(
				account -> snapshotRepository.existsByAppUserAndAccountIdAndSnapshotDateAfter(appUser, account.getId(), twoWeeksAgo));

		if (anyExists) {
			throw new IllegalStateException("Snapshots already exist for some accounts in the last 2 weeks.");
		}

		List<AccountBalanceSnapshot> snapshots = new ArrayList<>();
		for (Account account : accounts) {
			AccountBalanceSnapshot snapshot = AccountBalanceSnapshot.builder().account(account)
					.balance(account.getBalance()).snapshotDate(snapshotDate).appUser(appUser).build();

			snapshots.add(snapshot);
		}

		snapshotRepository.saveAll(snapshots);
	}

}
