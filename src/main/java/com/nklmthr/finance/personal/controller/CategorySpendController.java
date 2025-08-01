package com.nklmthr.finance.personal.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nklmthr.finance.personal.dto.CategorySpendDTO;
import com.nklmthr.finance.personal.service.CategorySpendService;

@RestController
@RequestMapping("/api/category-spends")
public class CategorySpendController {

	@Autowired
	private CategorySpendService categorySpendService;

	@GetMapping
	public ResponseEntity<List<CategorySpendDTO>> getCategorySpendForMonth(@RequestParam("month") Integer month,
			@RequestParam("year") Integer year) {
		List<CategorySpendDTO> spends = categorySpendService.getMonthlyCategorySpendHierarchy(month, year);
		return ResponseEntity.ok(spends);
	}
}
