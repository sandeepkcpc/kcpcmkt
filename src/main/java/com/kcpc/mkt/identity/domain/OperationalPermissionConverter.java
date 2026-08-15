package com.kcpc.mkt.identity.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Maps {@link OperationalPermission} to its INTEGER permission_number column value (1..17). */
@Converter(autoApply = true)
public class OperationalPermissionConverter implements AttributeConverter<OperationalPermission, Integer> {

    @Override
    public Integer convertToDatabaseColumn(OperationalPermission attribute) {
        return attribute == null ? null : attribute.number();
    }

    @Override
    public OperationalPermission convertToEntityAttribute(Integer dbData) {
        return dbData == null ? null : OperationalPermission.fromNumber(dbData);
    }
}
