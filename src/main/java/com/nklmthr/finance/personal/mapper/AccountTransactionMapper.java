package com.nklmthr.finance.personal.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.nklmthr.finance.personal.dto.AccountTransactionDTO;
import com.nklmthr.finance.personal.model.AccountTransaction;

@Mapper(componentModel = "spring", uses = { AccountMapper.class, CategoryMapper.class })
public interface AccountTransactionMapper extends GenericMapper<AccountTransactionDTO, AccountTransaction> {

	@Override
	@Mapping(target = "parentId", source = "parent.id")
	AccountTransactionDTO toDTO(AccountTransaction entity);

	
	@Mapping(target="href", ignore = true)
	@Mapping(target="hrefText", ignore = true)
	@Mapping(target="dataVersionId", ignore = true)
	@Mapping(target="sourceId", ignore = true)
	@Mapping(target="sourceThreadId", ignore = true)
	@Mapping(target="sourceTime", ignore = true)
	@Mapping(target = "uploadedStatement", ignore = true)
	@Mapping(target = "parent", ignore = true) 
	@Mapping(target = "appUser", ignore = true)
	AccountTransaction toEntity(AccountTransactionDTO dto);

	List<AccountTransactionDTO> toDTOList(List<AccountTransaction> entities);

	List<AccountTransaction> toEntityList(List<AccountTransactionDTO> dtos);
}
