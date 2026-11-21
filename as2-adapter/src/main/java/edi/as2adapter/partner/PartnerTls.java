package edi.as2adapter.partner;

import edi.as2adapter.as2.As2Exception;
import java.net.URI;
import java.net.http.HttpClient;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PartnerTls {

    private static final Logger LOG = LoggerFactory.getLogger(PartnerTls.class);
    private final ConcurrentHashMap<UUID, CachedContext> cache = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final boolean allowUntrusted;

    public PartnerTls(@Value("${as2.tls.cache-ttl-seconds:300}") long ttlSeconds,
                      @Value("${as2.tls.allow-untrusted:false}") boolean allowUntrusted) {
        this.ttl = Duration.ofSeconds(ttlSeconds);
        this.allowUntrusted = allowUntrusted;
    }

    public HttpClient client(UUID id, URI target, Duration connectTimeout, List<X509Certificate> certificates) {
        HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER);
        if ("https".equalsIgnoreCase(target.getScheme())) {
            Instant now = Instant.now();
            CachedContext trust = cache.compute(id, (key, previous) ->
                    previous != null && now.isBefore(previous.expires()) && previous.certificates().equals(certificates)
                            ? previous : new CachedContext(context(id, certificates), now.plus(ttl), List.copyOf(certificates)));
            SSLParameters parameters = new SSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            builder.sslContext(trust.context()).sslParameters(parameters);
        }
        return builder.build();
    }

    public void invalidate(UUID id) {
        cache.remove(id);
    }

    private SSLContext context(UUID partnerId, List<X509Certificate> certificates) {
        try {
            KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
            store.load(null, null);
            for (int i = 0; i < certificates.size(); i++) {
                store.setCertificateEntry("socio-" + i, certificates.get(i));
            }
            boolean tolerate = allowUntrusted && certificates.isEmpty();
            if (tolerate) LOG.warn("Confianza TLS permisiva para un socio sin certificado SSL: {}", partnerId);
            var trust = new CompositeTrust(manager(null), manager(store), tolerate);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[] {trust}, null);
            return context;
        } catch (GeneralSecurityException | java.io.IOException e) {
            throw new As2Exception("No se pudo preparar la confianza TLS del socio", e);
        }
    }

    private static X509TrustManager manager(KeyStore store) throws GeneralSecurityException {
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(store);
        return Arrays.stream(factory.getTrustManagers()).filter(X509TrustManager.class::isInstance)
                .map(X509TrustManager.class::cast).findFirst().orElseThrow();
    }

    private record CachedContext(SSLContext context, Instant expires, List<X509Certificate> certificates) {}

    private record CompositeTrust(X509TrustManager system, X509TrustManager partner,
                                  boolean tolerate) implements X509TrustManager {

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            try {
                system.checkServerTrusted(chain, authType);
            } catch (CertificateException e) {
                if (tolerate && chain.length > 0) {
                    chain[0].checkValidity();
                } else if (partner.getAcceptedIssuers().length > 0) {
                    partner.checkServerTrusted(chain, authType);
                } else {
                    throw e;
                }
            }
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            system.checkClientTrusted(chain, authType);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return system.getAcceptedIssuers();
        }
    }
}
