package edi.partners.dto;

/**
 * Lo que el motor AS2 necesita de un socio para recibir o enviar: el socio y sus configuraciones
 * vigentes (las que falten van en {@code null}). No incluye la clave de autenticacion basica.
 */
public record InternalPartnerView(PartnerResponse partner, OutgoingConfigResponse outgoing,
                                  IncomingConfigResponse incoming, MdnConfigResponse mdn) {
}
