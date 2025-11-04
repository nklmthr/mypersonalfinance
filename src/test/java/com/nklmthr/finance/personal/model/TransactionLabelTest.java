package com.nklmthr.finance.personal.model;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.nklmthr.finance.personal.enums.TransactionType;

class TransactionLabelTest {

	private AppUser appUser;
	private Account account;
	private AccountTransaction transaction;
	private Label label;
	private TransactionLabel transactionLabel;

	@BeforeEach
	void setUp() {
		appUser = new AppUser();
		appUser.setId("user-123");
		appUser.setUsername("testuser");

		account = new Account();
		account.setId("account-123");
		account.setName("Test Account");
		account.setBalance(BigDecimal.valueOf(1000.00));
		account.setAppUser(appUser);

		Category category = new Category();
		category.setId("category-123");
		category.setName("Test Category");

		transaction = AccountTransaction.builder()
			.id("tx-123")
			.date(LocalDateTime.now())
			.amount(BigDecimal.valueOf(100.00))
			.description("Test Transaction")
			.type(TransactionType.DEBIT)
			.account(account)
			.category(category)
			.appUser(appUser)
			.gptAccount(account)
			.build();

		label = new Label();
		label.setId("label-123");
		label.setName("Important");
		label.setAppUser(appUser);

		transactionLabel = TransactionLabel.builder()
			.id("tl-123")
			.transaction(transaction)
			.label(label)
			.appUser(appUser)
			.createdAt(LocalDateTime.now())
			.build();
	}

	@Test
	void testTransactionLabelCreation() {
		assertNotNull(transactionLabel);
		assertEquals("tl-123", transactionLabel.getId());
		assertEquals(transaction, transactionLabel.getTransaction());
		assertEquals(label, transactionLabel.getLabel());
		assertEquals(appUser, transactionLabel.getAppUser());
		assertNotNull(transactionLabel.getCreatedAt());
	}

	@Test
	void testTransactionLabelBuilder() {
		LocalDateTime now = LocalDateTime.now();
		TransactionLabel tl = TransactionLabel.builder()
			.id("new-tl-123")
			.transaction(transaction)
			.label(label)
			.appUser(appUser)
			.createdAt(now)
			.build();

		assertEquals("new-tl-123", tl.getId());
		assertEquals(transaction, tl.getTransaction());
		assertEquals(label, tl.getLabel());
		assertEquals(appUser, tl.getAppUser());
		assertEquals(now, tl.getCreatedAt());
	}

	@Test
	void testTransactionLabelRelationships() {
		// Verify bidirectional relationships
		assertEquals("tx-123", transactionLabel.getTransaction().getId());
		assertEquals("label-123", transactionLabel.getLabel().getId());
		assertEquals("user-123", transactionLabel.getAppUser().getId());
	}

	@Test
	void testTransactionLabelEqualsAndHashCode() {
		TransactionLabel tl1 = TransactionLabel.builder()
			.id("tl-1")
			.transaction(transaction)
			.label(label)
			.appUser(appUser)
			.build();

		TransactionLabel tl2 = TransactionLabel.builder()
			.id("tl-1")
			.transaction(transaction)
			.label(label)
			.appUser(appUser)
			.build();

		TransactionLabel tl3 = TransactionLabel.builder()
			.id("tl-2")
			.transaction(transaction)
			.label(label)
			.appUser(appUser)
			.build();

		assertEquals(tl1, tl2);
		assertNotEquals(tl1, tl3);
		assertEquals(tl1.hashCode(), tl2.hashCode());
	}

	@Test
	void testTransactionHelperMethods() {
		// Test addLabel helper
		AccountTransaction tx = AccountTransaction.builder()
			.id("tx-456")
			.date(LocalDateTime.now())
			.amount(BigDecimal.valueOf(200.00))
			.description("Test Transaction 2")
			.type(TransactionType.CREDIT)
			.account(account)
			.appUser(appUser)
			.gptAccount(account)
			.build();

		tx.addLabel(label, appUser);
		assertEquals(1, tx.getTransactionLabels().size());
		assertEquals(1, tx.getLabels().size());
		assertEquals("Important", tx.getLabels().get(0).getName());

		// Test removeLabel helper
		tx.removeLabel(label);
		assertEquals(0, tx.getTransactionLabels().size());
		assertEquals(0, tx.getLabels().size());
	}

	@Test
	void testTransactionSetLabelsHelper() {
		AccountTransaction tx = AccountTransaction.builder()
			.id("tx-789")
			.date(LocalDateTime.now())
			.amount(BigDecimal.valueOf(300.00))
			.description("Test Transaction 3")
			.type(TransactionType.DEBIT)
			.account(account)
			.appUser(appUser)
			.gptAccount(account)
			.build();

		Label label2 = new Label();
		label2.setId("label-456");
		label2.setName("Urgent");
		label2.setAppUser(appUser);

		java.util.List<Label> labels = java.util.List.of(label, label2);
		tx.setLabels(labels, appUser);

		assertEquals(2, tx.getTransactionLabels().size());
		assertEquals(2, tx.getLabels().size());
		assertTrue(tx.getLabels().stream().anyMatch(l -> l.getName().equals("Important")));
		assertTrue(tx.getLabels().stream().anyMatch(l -> l.getName().equals("Urgent")));
	}
}

