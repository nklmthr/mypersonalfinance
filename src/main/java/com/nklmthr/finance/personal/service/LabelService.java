package com.nklmthr.finance.personal.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.nklmthr.finance.personal.dto.LabelDTO;
import com.nklmthr.finance.personal.mapper.LabelMapper;
import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.model.Label;
import com.nklmthr.finance.personal.repository.LabelRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LabelService {

	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(LabelService.class);

	@Autowired
	private final AppUserService appUserService;

	@Autowired
	private final LabelRepository labelRepository;

	@Autowired
	private final LabelMapper labelMapper;

	private static final int MAX_AUTOCOMPLETE_RESULTS = 10;

	public List<LabelDTO> getAllLabels() {
		return getAllLabels(appUserService.getCurrentUser());
	}

	public List<LabelDTO> getAllLabels(AppUser appUser) {
		logger.debug("Fetching all labels for user: {}", appUser.getUsername());
		List<Label> labels = labelRepository.findByAppUser(appUser);
		return labelMapper.toDTOList(labels);
	}

	public List<LabelDTO> searchLabels(String query) {
		return searchLabels(appUserService.getCurrentUser(), query);
	}

	public List<LabelDTO> searchLabels(AppUser appUser, String query) {
		logger.debug("Searching labels for user: {} with query: {}", appUser.getUsername(), query);
		if (query == null || query.trim().isEmpty()) {
			return getAllLabels(appUser);
		}
		Pageable pageable = PageRequest.of(0, MAX_AUTOCOMPLETE_RESULTS);
		List<Label> labels = labelRepository.findByAppUserAndNameContainingIgnoreCase(appUser, query.trim(), pageable);
		return labelMapper.toDTOList(labels);
	}

	public Label findOrCreateLabel(String name) {
		return findOrCreateLabel(appUserService.getCurrentUser(), name);
	}

	public Label findOrCreateLabel(AppUser appUser, String name) {
		if (name == null || name.trim().isEmpty()) {
			throw new IllegalArgumentException("Label name cannot be empty");
		}
		String trimmedName = name.trim();
		logger.debug("Finding or creating label: {} for user: {}", trimmedName, appUser.getUsername());
		
		return labelRepository.findByAppUserAndNameIgnoreCase(appUser, trimmedName)
			.orElseGet(() -> {
				Label newLabel = new Label();
				newLabel.setName(trimmedName);
				newLabel.setAppUser(appUser);
				logger.info("Creating new label: {} for user: {}", trimmedName, appUser.getUsername());
				return labelRepository.save(newLabel);
			});
	}

	public LabelDTO createLabel(LabelDTO labelDTO) {
		return createLabel(appUserService.getCurrentUser(), labelDTO);
	}

	public LabelDTO createLabel(AppUser appUser, LabelDTO labelDTO) {
		if (labelDTO.name() == null || labelDTO.name().trim().isEmpty()) {
			throw new IllegalArgumentException("Label name cannot be empty");
		}
		Label label = findOrCreateLabel(appUser, labelDTO.name());
		return labelMapper.toDTO(label);
	}
}

