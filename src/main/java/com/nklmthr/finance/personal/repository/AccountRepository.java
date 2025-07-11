package com.nklmthr.finance.personal.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.nklmthr.finance.personal.model.Account;

@Repository
public interface AccountRepository extends JpaRepository<Account, String> {
    boolean existsByName(String name);
    
    List<Account> findByAccountTypeId(String accountTypeId);
    List<Account> findByInstitutionId(String institutionId);
    List<Account> findByAccountTypeIdAndInstitutionId(String accountTypeId, String institutionId);

	Optional<Account> findByName(String string);
	
	@Query("" +
			"SELECT a FROM Account a " +
			"LEFT JOIN FETCH a.accountType " +
			"LEFT JOIN FETCH a.institution " +
			"ORDER BY a.name")
	List<Account> findAll();

}
