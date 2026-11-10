package edi.as2adapter.spike;

import edi.as2adapter.as2.As2Client;
import edi.as2adapter.as2.As2Exception;
import edi.as2adapter.as2.As2Identity;
import edi.as2adapter.as2.MessageBuilder;
import edi.as2adapter.as2.OutboundMessage;
import edi.as2adapter.as2.SendResult;
import java.net.http.HttpClient;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spike AS2-94: envia un documento al socio configurado y devuelve lo que paso con su MDN. Solo existe
 * con {@code as2.spike.enabled=true} y no se publica por el gateway; la integracion con la consola y el
 * repositorio llega con HU-15.
 */
@RestController
@ConditionalOnProperty(name = "as2.spike.enabled", havingValue = "true")
@EnableConfigurationProperties(SpikeProperties.class)
public class SpikeController {

    private static final Logger log = LoggerFactory.getLogger(SpikeController.class);

    private final SpikeProperties props;
    private final As2Identity identity;
    private final X509Certificate partner;
    private final MessageBuilder builder;
    private final As2Client client;

    /** El que usa Spring; el otro es para las pruebas. Con dos constructores hay que indicarlo. */
    @Autowired
    public SpikeController(SpikeProperties props) {
        this(props, Clock.systemDefaultZone(),
                new As2Client(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), Duration.ofSeconds(60)));
    }

    SpikeController(SpikeProperties props, Clock clock, As2Client client) {
        this.props = props;
        this.identity = As2Identity.fromPkcs12(props.keystore(), props.keystorePassword().toCharArray(), props.keyAlias());
        this.partner = As2Identity.certificate(props.partnerCertificate());
        this.builder = new MessageBuilder(clock);
        this.client = client;
    }

    /** Envia el cuerpo (un X12, por ejemplo) como {@code fileName} y resume el MDN. */
    @PostMapping("/spike/send")
    public Map<String, Object> send(@RequestBody byte[] document,
                                    @RequestParam(defaultValue = "documento.edi") String fileName) {
        OutboundMessage msg = builder.build(new MessageBuilder.Request(props.from(), props.to(), props.mdnTo(), document,
                "application/edi-x12", fileName, identity, partner));
        log.info("Enviando {} ({} bytes) a {} como {}", fileName, document.length, props.url(), msg.messageId());
        SendResult r = client.send(props.url(), msg, partner);
        log.info("MDN de {}: {} (firma {}, MIC {})", msg.messageId(), r.mdn().disposition(),
                r.mdn().signatureValid() ? "valida" : "NO valida", r.micMatches() ? "coincide" : "NO coincide");

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("delivered", r.delivered());
        out.put("messageId", r.messageId());
        out.put("httpStatus", r.httpStatus());
        out.put("elapsedMs", r.elapsed().toMillis());
        out.put("disposition", r.mdn().disposition());
        out.put("mdnSigned", r.mdn().signed());
        out.put("mdnSignatureValid", r.mdn().signatureValid());
        out.put("micSent", r.mic());
        out.put("micReceived", r.mdn().receivedContentMic());
        out.put("micAlgorithm", r.mdn().micAlgorithm());
        out.put("micMatches", r.micMatches());
        out.put("originalMessageId", r.mdn().originalMessageId());
        out.put("text", r.mdn().text());
        return out;
    }

    @ExceptionHandler(As2Exception.class)
    ResponseEntity<Map<String, String>> failed(As2Exception e) {
        log.warn("Envio de prueba fallido: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", e.getMessage()));
    }
}
