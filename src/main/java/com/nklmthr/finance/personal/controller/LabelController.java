package com.nklmthr.finance.personal.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nklmthr.finance.personal.dto.LabelDTO;
import com.nklmthr.finance.personal.service.LabelService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/labels")
@RequiredArgsConstructor
public class LabelController {

	private final LabelService labelService;

	@GetMapping
	public List<LabelDTO> getAll() {
		return labelService.getAllLabels();
	}

	@GetMapping("/search")
	public List<LabelDTO> search(@RequestParam(required = false) String q) {
		return labelService.searchLabels(q);
	}

	@PostMapping
	public LabelDTO create(@RequestBody LabelDTO label) {
		return labelService.createLabel(label);
	}
}

