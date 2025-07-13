package com.nklmthr.finance.personal.service;

import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.model.Institution;
import com.nklmthr.finance.personal.repository.InstitutionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class InstitutionService {
	@Autowired
	private AppUserService appUserService;

	@Autowired
    private InstitutionRepository institutionRepository;

    
    public List<Institution> getAllInstitutions() {
    	AppUser appUser = appUserService.getCurrentUser();
        return institutionRepository.findAllByAppUser(appUser);
    }

    public Optional<Institution> getInstitutionById(String id) {
    	AppUser appUser = appUserService.getCurrentUser();
        return institutionRepository.findByAppUserAndId(appUser, id);
    }

    public Institution createInstitution(Institution institution) {
    	AppUser appUser = appUserService.getCurrentUser();
        if (institutionRepository.existsByAppUserAndName(appUser, institution.getName())) {
            throw new IllegalArgumentException("Institution with name already exists");
        }
        institution.setAppUser(appUser); // Set the current user as the owner of the institution
        return institutionRepository.save(institution);
    }

    public Institution updateInstitution(String id, Institution updatedInstitution) {
    	AppUser appUser = appUserService.getCurrentUser();
        return institutionRepository.findByAppUserAndId(appUser, id).map(institution -> {
            institution.setName(updatedInstitution.getName());
            institution.setDescription(updatedInstitution.getDescription());
            institution.setAppUser(appUser); 
            return institutionRepository.save(institution);
        }).orElseThrow(() -> new IllegalArgumentException("Institution not found with id " + id));
    }

    public void deleteInstitution(String id) {
    	AppUser appUser = appUserService.getCurrentUser();
        if (institutionRepository.findByAppUserAndId(appUser, id).isEmpty()) {
            throw new IllegalArgumentException("Institution not found with id " + id);
        }
        institutionRepository.deleteByAppUserAndId(appUser, id);
    }
}
