package edi.documents.web;

import static org.assertj.core.api.Assertions.assertThat;

import edi.documents.web.GenerateRequest.Company;
import edi.documents.web.GenerateRequest.Line;
import edi.documents.web.GenerateRequest.Party;
import edi.documents.web.GenerateRequest.PurchaseOrder;
import edi.documents.web.GenerateRequest.Promotion;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Fechas por defecto de la orden de compra y de la promocion: explicita, por dias o la de la regla. */
class X12RequestMapperTest {

    private final X12RequestMapper mapper = new X12RequestMapper(
            Clock.fixed(Instant.parse("2026-11-01T14:30:00Z"), ZoneId.of("America/Guayaquil")));

    private static final Company BUYER = new Company("Comprador Ejemplo", "COMP-01");
    private static final Company SELLER = new Company("Proveedor Ejemplo", "PROV-01");
    private static final Party SENDER = new Party("12", "ID-SOCIO");
    private static final Party RECEIVER = new Party("08", "ID-EMPRESA");
    private static final List<Line> LINES = List.of(new Line(BigDecimal.ONE, null, BigDecimal.TEN, "ROSA-60", null));

    private String po(LocalDate deliveryDate, Integer deliveryDays) {
        return mapper.generate(new GenerateRequest("850", null, null, 1L, SENDER, RECEIVER,
                new PurchaseOrder("OC-1", null, null, null, null, deliveryDate, deliveryDays, BUYER, SELLER, LINES),
                null, null));
    }

    private String promo(LocalDate end, Integer days) {
        return mapper.generate(new GenerateRequest("889", null, null, 1L, SENDER, RECEIVER, null,
                new Promotion("PROMO-1", null, end, days, SELLER), null));
    }

    @Test
    void entrega_porDefecto_porDias_oExplicita() {
        assertThat(po(null, null)).contains("DTM*002*20261108~");
        assertThat(po(null, 10)).contains("DTM*002*20261111~");
        assertThat(po(LocalDate.of(2026, 12, 24), 10)).contains("DTM*002*20261224~");
    }

    @Test
    void finDePromocion_porDefecto_porDias_oExplicito() {
        assertThat(promo(null, null)).contains("DTM*195*20261201~");
        assertThat(promo(null, 5)).contains("DTM*195*20261106~");
        assertThat(promo(LocalDate.of(2026, 11, 20), 5)).contains("DTM*195*20261120~");
    }
}
