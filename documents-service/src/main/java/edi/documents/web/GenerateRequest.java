package edi.documents.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Peticion de {@code POST /api/documents/x12/generate}. Solo {@code type} y los datos de su documento
 * son obligatorios; lo demas toma un valor por defecto (version 4010, modo pruebas, fecha de hoy,
 * entrega a 7 dias, promocion de 30 dias, numero de control segun la hora).
 *
 * @param type          850, 997 u 889
 * @param original      solo 997: el texto del documento que se acusa
 */
public record GenerateRequest(
        String type,
        String version,
        Boolean production,
        Long controlNumber,
        Party sender,
        Party receiver,
        PurchaseOrder purchaseOrder,
        Promotion promotion,
        String original) {

    public record Party(String qualifier, String id) {
    }

    public record Company(String name, String id) {
    }

    public record Line(BigDecimal quantity, String unit, BigDecimal unitPrice, String productId, String description) {
    }

    /** @param deliveryDays dias hasta la entrega si no se da {@code deliveryDate} */
    public record PurchaseOrder(String number, LocalDate date, String purpose, String type, String reference,
            LocalDate deliveryDate, Integer deliveryDays, Company buyer, Company seller, List<Line> lines) {
    }

    /** @param days duracion de la promocion si no se da {@code end} */
    public record Promotion(String id, LocalDate start, LocalDate end, Integer days, Company vendor) {
    }
}
