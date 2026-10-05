package edi.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RoleSetConverterTest {

    private final RoleSetConverter conv = new RoleSetConverter();

    @Test
    void aColumna_csvOrdenado() {
        assertThat(conv.convertToDatabaseColumn(EnumSet.of(Role.OPERATOR, Role.ADMIN))).isEqualTo("ADMIN,OPERATOR");
        assertThat(conv.convertToDatabaseColumn(Set.of())).isEmpty();
        assertThat(conv.convertToDatabaseColumn(null)).isEmpty();
    }

    @Test
    void aEntidad_toleraEspaciosYVacios() {
        assertThat(conv.convertToEntityAttribute(" ADMIN , ,AUDITOR")).containsExactlyInAnyOrder(Role.ADMIN, Role.AUDITOR);
        assertThat(conv.convertToEntityAttribute("")).isEmpty();
        assertThat(conv.convertToEntityAttribute(null)).isEmpty();
    }
}
