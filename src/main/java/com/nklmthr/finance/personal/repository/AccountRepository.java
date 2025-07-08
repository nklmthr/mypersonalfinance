package com.nklmthr.finance.personal.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nklmthr.finance.personal.model.Account;

@Repository
public interface AccountRepository extends JpaRepository<Account, String> {
    boolean existsByName(String name);
    
    List<Account> findByAccountTypeId(Long accountTypeId);
    List<Account> findByInstitutionId(Long institutionId);
    List<Account> findByAccountTypeIdAndInstitutionId(Long accountTypeId, Long institutionId);

	Optional<Account> findByName(String string);

}
