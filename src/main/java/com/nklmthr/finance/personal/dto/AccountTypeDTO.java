package com.nklmthr.finance.personal.dto;

import java.math.BigDecimal;

public record AccountTypeDTO(String id, String name, String description, String classification, BigDecimal accountTypeBalance) {

}
