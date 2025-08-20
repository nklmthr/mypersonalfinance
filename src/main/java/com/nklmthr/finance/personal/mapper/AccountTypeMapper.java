package com.nklmthr.finance.personal.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.nklmthr.finance.personal.dto.AccountTypeDTO;
import com.nklmthr.finance.personal.model.AccountType;

@Mapper(componentModel = "spring")
public interface AccountTypeMapper extends GenericMapper<AccountTypeDTO, AccountType> {

    @Override
    AccountTypeDTO toDTO(AccountType entity);

    @Mapping(target = "appUser", ignore = true)
    AccountType toEntity(AccountTypeDTO dto);
}
