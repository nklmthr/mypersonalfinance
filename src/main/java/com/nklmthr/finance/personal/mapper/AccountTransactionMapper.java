package com.nklmthr.finance.personal.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.nklmthr.finance.personal.dto.AccountTransactionDTO;
import com.nklmthr.finance.personal.model.AccountTransaction;


@Mapper(componentModel = "spring", uses = { AccountMapper.class, CategoryMapper.class })
public interface AccountTransactionMapper extends GenericMapper<AccountTransactionDTO, AccountTransaction> {


	@Override
	@Mapping(target = "parentId", source = "parent")
	@Mapping(target = "linkedTransferId", source = "linkedTransferId")
	@Mapping(target = "gptAmount", source = "gptAmount")
	@Mapping(target = "gptDescription", source = "gptDescription")
	@Mapping(target = "gptExplanation", source = "gptExplanation")
	@Mapping(target = "gptType", source = "gptType")
	@Mapping(target = "currency", source = "currency")
	@Mapping(target = "gptAccount", source = "gptAccount")
	@Mapping(target="shortDescription", ignore = true)
	@Mapping(target="shortExplanation", ignore = true)
	@Mapping(target="children", ignore = true)
	AccountTransactionDTO toDTO(AccountTransaction entity);

	@Mapping(target="href", ignore = true)
	@Mapping(target="hrefText", ignore = true)
	@Mapping(target="dataVersionId", ignore = true)
	@Mapping(target="sourceId", ignore = true)
	@Mapping(target="sourceThreadId", ignore = true)
	@Mapping(target="sourceTime", ignore = true)
	@Mapping(target = "uploadedStatement", ignore = true)
	@Mapping(target = "parent", source = "parentId")
	@Mapping(target = "linkedTransferId", source = "linkedTransferId")
	@Mapping(target = "appUser", ignore = true)
	@Mapping(target = "rawData" , ignore = true)
	@Mapping(target = "gptAmount", source = "gptAmount")
	@Mapping(target = "gptDescription", source = "gptDescription")
	@Mapping(target = "gptExplanation", source = "gptExplanation")
	@Mapping(target = "gptType", source = "gptType")
	@Mapping(target = "currency", source = "currency")
	@Mapping(target = "gptAccount", source = "gptAccount")
	AccountTransaction toEntity(AccountTransactionDTO dto);

	@Mapping(target="shortDescription", ignore = true)
	@Mapping(target="shortExplanation", ignore = true)
	List<AccountTransactionDTO> toDTOList(List<AccountTransaction> entities);

	List<AccountTransaction> toEntityList(List<AccountTransactionDTO> dtos);
}
