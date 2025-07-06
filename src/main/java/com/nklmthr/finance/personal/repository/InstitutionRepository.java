package com.nklmthr.finance.personal.repository;

import com.nklmthr.finance.personal.model.Institution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InstitutionRepository extends JpaRepository<Institution, Long> {

    Institution findByName(String name);

    boolean existsByName(String name);
}
