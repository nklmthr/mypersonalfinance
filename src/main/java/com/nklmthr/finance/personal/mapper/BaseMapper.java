package com.nklmthr.finance.personal.mapper;

import java.util.List;
import java.util.stream.Collectors;

public abstract class BaseMapper<D, E> implements GenericMapper<D, E> {

    @Override
    public List<D> toDTOList(List<E> entities) {
        if (entities == null) return List.of();
        return entities.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public List<E> toEntityList(List<D> dtos) {
        if (dtos == null) return List.of();
        return dtos.stream().map(this::toEntity).collect(Collectors.toList());
    }
}