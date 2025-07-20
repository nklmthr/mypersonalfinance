package com.nklmthr.finance.personal.controller;

import com.nklmthr.finance.personal.dto.BalanceSheetDTO;
import com.nklmthr.finance.personal.service.BalanceSheetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
