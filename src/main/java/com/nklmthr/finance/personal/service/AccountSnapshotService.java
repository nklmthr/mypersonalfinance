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
	@Autowired
	AccountRepository accountRepository;
	@Autowired
	AccountBalanceSnapshotRepository snapshotRepository;
	@Autowired
	AppUserService appUserService;
	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(AccountSnapshotService.class);

	public void createSnapshotsForDate(LocalDateTime snapshotDate) {
		logger.info("Creating snapshots for date: " + snapshotDate);
		AppUser appUser = appUserService.getCurrentUser();
		LocalDateTime twoWeeksAgo = snapshotDate.minusDays(14);
		List<Account> accounts = accountRepository.findAll();

		// 🔍 Get all recent snapshot records for this user in one DB call
		List<AccountBalanceSnapshot> recentSnapshots = snapshotRepository.findByAppUserAndSnapshotDateAfter(appUser,
				twoWeeksAgo);

		if (!recentSnapshots.isEmpty()) {
			logger.warn("Snapshots already exist for some accounts in the last 2 weeks.");
			throw new IllegalStateException("Snapshots already exist for some accounts in the last 2 weeks.");
		}

		List<AccountBalanceSnapshot> snapshots = new ArrayList<>();
		for (Account account : accounts) {
			logger.info(
					"Creating snapshot for account: " + account.getName() + " with balance: " + account.getBalance());
			AccountBalanceSnapshot snapshot = AccountBalanceSnapshot.builder().account(account)
					.balance(account.getBalance()).snapshotDate(snapshotDate).appUser(appUser).build();
			snapshots.add(snapshot);
		}
		logger.info("Saving " + snapshots.size() + " snapshots to the database.");
		snapshotRepository.saveAll(snapshots);
	}

}
