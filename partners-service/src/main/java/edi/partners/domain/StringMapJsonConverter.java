package edi.partners.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Campos adicionales {nombre: valor} guardados como JSON en una columna de texto. */
@Converter
public class StringMapJsonConverter implements AttributeConverter<Map<String, String>, String> {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<LinkedHashMap<String, String>> TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(Map<String, String> fields) {
        return fields == null || fields.isEmpty() ? null : JSON.writeValueAsString(fields);
    }

    @Override
    public Map<String, String> convertToEntityAttribute(String column) {
        return column == null || column.isBlank() ? new LinkedHashMap<>() : JSON.readValue(column, TYPE);
    }
}
