package com.kcpc.mkt.identity.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Maps {@link LifecycleStage} to its INTEGER stage_number column value (1..7). */
@Converter(autoApply = true)
public class LifecycleStageConverter implements AttributeConverter<LifecycleStage, Integer> {

    @Override
    public Integer convertToDatabaseColumn(LifecycleStage attribute) {
        return attribute == null ? null : attribute.number();
    }

    @Override
    public LifecycleStage convertToEntityAttribute(Integer dbData) {
        return dbData == null ? null : LifecycleStage.fromNumber(dbData);
    }
}
