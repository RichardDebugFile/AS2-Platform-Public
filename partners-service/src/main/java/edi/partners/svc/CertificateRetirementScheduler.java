package edi.partners.svc;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Retira cada dia los certificados caducados que ya tienen reemplazo vigente (HU-11). Corre por
 * defecto a las 06:30 de Ecuador, antes de la vigilancia de caducidad de las 08:00 (HU-21).
 */
@Component
public class CertificateRetirementScheduler {

    private static final Logger log = LoggerFactory.getLogger(CertificateRetirementScheduler.class);

    private final PartnerCertificateService certificates;
    private final Clock clock;

    public CertificateRetirementScheduler(PartnerCertificateService certificates, Clock clock) {
        this.certificates = certificates;
        this.clock = clock;
    }

    @Scheduled(cron = "${partners.certificates.retire-cron:0 30 6 * * *}",
            zone = "${partners.certificates.zone:America/Guayaquil}")
    public void run() {
        PartnerCertificateService.RetirementResult r = certificates.retireExpired(clock.instant());
        log.info("Retiro de certificados caducados: {} retirados, {} conservados por ser el unico activo",
                r.retired(), r.kept());
    }
}
