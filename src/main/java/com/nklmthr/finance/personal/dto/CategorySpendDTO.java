package com.nklmthr.finance.personal.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategorySpendDTO {
    private String id;
    private String name;
    private String parentId;
    private List<MonthlySpend> monthlySpends = new ArrayList<>();
    private List<CategorySpendDTO> children = new ArrayList<>();
}
