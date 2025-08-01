package com.nklmthr.finance.personal.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.model.Institution;
import com.nklmthr.finance.personal.repository.InstitutionRepository;

@Service
public class InstitutionService {
	@Autowired
	private AppUserService appUserService;

	@Autowired
	private InstitutionRepository institutionRepository;

	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(InstitutionService.class);

	public List<Institution> getAllInstitutions() {
		AppUser appUser = appUserService.getCurrentUser();
		logger.info("Fetching all institutions for user: {}", appUser.getUsername());
		return institutionRepository.findAllByAppUser(appUser);
	}

	public Optional<Institution> getInstitutionById(String id) {
		AppUser appUser = appUserService.getCurrentUser();
		logger.info("Fetching institution by id: {} for user: {}", id, appUser.getUsername());
		return institutionRepository.findByAppUserAndId(appUser, id);
	}

	public Institution createInstitution(Institution institution) {
		AppUser appUser = appUserService.getCurrentUser();
		if (institutionRepository.existsByAppUserAndName(appUser, institution.getName())) {
			logger.error("Institution with name already exists: {}", institution.getName());
			throw new IllegalArgumentException("Institution with name already exists");
		}
		institution.setAppUser(appUser); // Set the current user as the owner of the institution
		logger.info("Creating institution for user: {} with name: {}", appUser.getUsername(), institution.getName());
		return institutionRepository.save(institution);
	}

	public Institution updateInstitution(String id, Institution updatedInstitution) {
		AppUser appUser = appUserService.getCurrentUser();
		return institutionRepository.findByAppUserAndId(appUser, id).map(institution -> {
			institution.setName(updatedInstitution.getName());
			institution.setDescription(updatedInstitution.getDescription());
			institution.setAppUser(appUser);
			logger.info("Updating institution with id: {} for user: {}", id, appUser.getUsername());
			return institutionRepository.save(institution);
		}).orElseThrow(() -> new IllegalArgumentException("Institution not found with id " + id));
	}

	public void deleteInstitution(String id) {
		AppUser appUser = appUserService.getCurrentUser();
		if (institutionRepository.findByAppUserAndId(appUser, id).isEmpty()) {
			logger.error("Institution not found with id: {}", id);
			throw new IllegalArgumentException("Institution not found with id " + id);
		}
		logger.info("Deleting institution with id: {} for user: {}", id, appUser.getUsername());
		institutionRepository.deleteByAppUserAndId(appUser, id);
	}
}
