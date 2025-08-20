package com.nklmthr.finance.personal.mapper;

import java.util.List;

public interface GenericMapper<D, E> {
	D toDTO(E entity);

	E toEntity(D dto);

	List<D> toDTOList(List<E> entities);

	List<E> toEntityList(List<D> dtos);
}