package edi.documents.web;

import edi.documents.schema.X12ValidationService;
import edi.documents.schema.X12ValidationService.ValidationResult;
import edi.documents.x12.X12Document;
import edi.documents.x12.X12Parser;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Documentos X12 (HU-12): interpretar, validar contra el esquema de su tipo y generar 850, 997 y 889.
 * El documento viaja como texto plano, tal como se intercambia con los socios.
 */
@RestController
@RequestMapping("/api/documents/x12")
public class X12Controller {

    private final X12ValidationService validation;
    private final X12RequestMapper generator;

    public X12Controller(X12ValidationService validation, X12RequestMapper generator) {
        this.validation = validation;
        this.generator = generator;
    }

    /** Resumen del documento: tipo, version, partes, numeros de control y separadores. */
    public record ParseResponse(String typeCode, String version, String functionalCode, X12Document.Party sender,
            X12Document.Party receiver, String groupSender, String groupReceiver, String interchangeDate,
            String interchangeTime, String usageIndicator, Map<String, String> controls, boolean complete,
            int segmentCount, String elementSeparator, String componentSeparator, String segmentTerminator) {

        static ParseResponse of(X12Document d) {
            return new ParseResponse(d.typeCode(), d.version(), d.functionalCode(), d.sender(), d.receiver(),
                    d.groupSender(), d.groupReceiver(), d.interchangeDate(), d.interchangeTime(), d.usageIndicator(),
                    d.controls(), d.complete(), d.segments().size(), String.valueOf(d.delimiters().element()),
                    String.valueOf(d.delimiters().component()), d.delimiters().segment());
        }
    }

    public record SchemaResponse(String typeCode, String description, List<String> requiredSegments) {
    }

    @PostMapping(value = "/parse", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ParseResponse parse(@RequestBody String text) {
        return ParseResponse.of(X12Parser.parse(text));
    }

    @PostMapping(value = "/validate", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ValidationResult validate(@RequestBody String text) {
        return validation.validate(text);
    }

    @PostMapping(value = "/generate", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public String generate(@RequestBody GenerateRequest request) {
        return generator.generate(request);
    }

    @GetMapping("/schemas")
    public List<SchemaResponse> schemas() {
        return validation.schemas().stream()
                .map(s -> new SchemaResponse(s.getTypeCode(), s.getDescription(), s.getRequiredSegments()))
                .toList();
    }
}
