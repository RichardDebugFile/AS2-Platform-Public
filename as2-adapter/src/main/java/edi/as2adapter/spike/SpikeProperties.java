package edi.as2adapter.spike;

import java.net.URI;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracion del envio de prueba (AS2-94). Se da por variables de entorno en el entorno de
 * interoperabilidad (deploy/interop); en la plataforma normal el spike esta apagado.
 *
 * @param enabled            activa el endpoint de prueba
 * @param url                URL AS2 del socio
 * @param from               identificador AS2 propio
 * @param to                 identificador AS2 del socio
 * @param mdnTo              direccion para Disposition-Notification-To
 * @param keystore           PKCS#12 con la clave y el certificado propios
 * @param keystorePassword   clave del PKCS#12
 * @param keyAlias           alias de la clave (opcional si hay una sola)
 * @param partnerCertificate certificado del socio (PEM o DER)
 */
@ConfigurationProperties("as2.spike")
public record SpikeProperties(boolean enabled, URI url, String from, String to, String mdnTo, Path keystore,
                              String keystorePassword, String keyAlias, Path partnerCertificate) {
}
