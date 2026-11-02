package edi.documents.schema;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Arrays;
import java.util.List;

/** Segmentos obligatorios de un tipo de documento X12 (tabla {@code document_schema}). */
@Entity
@Table(name = "document_schema")
public class DocumentSchema {

    @Id
    @Column(name = "type_code", length = 6)
    private String typeCode;

    @Column(nullable = false, length = 120)
    private String description;

    @Column(name = "required_segments", nullable = false, length = 500)
    private String requiredSegments;

    protected DocumentSchema() {
    }

    public DocumentSchema(String typeCode, String description, List<String> requiredSegments) {
        this.typeCode = typeCode;
        this.description = description;
        this.requiredSegments = String.join(",", requiredSegments);
    }

    public String getTypeCode() {
        return typeCode;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getRequiredSegments() {
        return Arrays.stream(requiredSegments.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }
}
