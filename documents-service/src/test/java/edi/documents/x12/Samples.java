package edi.documents.x12;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Documentos de muestra con datos ficticios (src/test/resources/x12). Estan escritos con {@code ~} y
 * un salto de linea por segmento; las variantes cambian solo el fin de segmento.
 */
public final class Samples {

    private Samples() {
    }

    public static String read(String name) {
        try (InputStream in = Samples.class.getResourceAsStream("/x12/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("Falta la muestra " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 850 version 4010, de emisor (12, ID-SOCIO) a receptor (08, ID-EMPRESA). */
    public static String po850() {
        return read("850-4010.edi");
    }

    /** El mismo 850 con solo {@code ~} como fin de segmento, en una sola linea. */
    public static String po850TildeOnly() {
        return po850().replace("\n", "");
    }

    /** El mismo 850 con salto de linea CRLF como fin de segmento (sin {@code ~}). */
    public static String po850Crlf() {
        return po850().replace("~\n", "\r\n");
    }

    /** El mismo 850 con salto de linea LF como fin de segmento (sin {@code ~}). */
    public static String po850Lf() {
        return po850().replace("~\n", "\n");
    }

    /** 997 version 5010 con {@code :} como separador de componentes. */
    public static String ack997() {
        return read("997-5010.edi");
    }
}
