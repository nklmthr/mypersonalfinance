package com.nklmthr.finance.personal.repository;

import com.nklmthr.finance.personal.model.AccountType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccountTypeRepository extends JpaRepository<AccountType, String> {

    Optional<AccountType> findByName(String name);

    boolean existsByName(String name);
}
