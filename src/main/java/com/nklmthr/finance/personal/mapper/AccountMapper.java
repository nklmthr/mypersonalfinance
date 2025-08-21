package com.nklmthr.finance.personal.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.nklmthr.finance.personal.dto.AccountDTO;
import com.nklmthr.finance.personal.model.Account;

@Mapper(componentModel = "spring", uses = { AccountTypeMapper.class, InstitutionMapper.class })
public interface AccountMapper extends GenericMapper<AccountDTO, Account> {
	
	AccountDTO toDTO(Account entity);

	@Mapping(target = "appUser", ignore = true)
	Account toEntity(AccountDTO dto);

}
