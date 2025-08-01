package com.nklmthr.finance.personal.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nklmthr.finance.personal.dto.BalanceSheetDTO;
import com.nklmthr.finance.personal.service.BalanceSheetService;

@RestController
@RequestMapping("/api/balance-sheet")
public class BalanceSheetController {

	@Autowired
	private BalanceSheetService balanceSheetService;

	// GET /api/balance-sheet/last-six-months
	@GetMapping("/year/{year}")
	public List<BalanceSheetDTO> getLastSixMonthsBalanceSheet(@PathVariable("year") int year) {
		return balanceSheetService.generateBalanceSheet(year);
	}
}
