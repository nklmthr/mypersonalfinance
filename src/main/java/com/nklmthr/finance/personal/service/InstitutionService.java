package com.nklmthr.finance.personal.service;

import com.nklmthr.finance.personal.model.Institution;
import com.nklmthr.finance.personal.repository.InstitutionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class InstitutionService {

    private final InstitutionRepository institutionRepository;

    @Autowired
    public InstitutionService(InstitutionRepository institutionRepository) {
        this.institutionRepository = institutionRepository;
    }

    public List<Institution> getAllInstitutions() {
        return institutionRepository.findAll();
    }

    public Optional<Institution> getInstitutionById(Long id) {
        return institutionRepository.findById(id);
    }

    public Institution createInstitution(Institution institution) {
        if (institutionRepository.existsByName(institution.getName())) {
            throw new IllegalArgumentException("Institution with name already exists");
        }
        return institutionRepository.save(institution);
    }

    public Institution updateInstitution(Long id, Institution updatedInstitution) {
        return institutionRepository.findById(id).map(institution -> {
            institution.setName(updatedInstitution.getName());
            institution.setDescription(updatedInstitution.getDescription());
            return institutionRepository.save(institution);
        }).orElseThrow(() -> new IllegalArgumentException("Institution not found with id " + id));
    }

    public void deleteInstitution(Long id) {
        if (!institutionRepository.existsById(id)) {
            throw new IllegalArgumentException("Institution not found with id " + id);
        }
        institutionRepository.deleteById(id);
    }
}
