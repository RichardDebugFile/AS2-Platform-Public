package edi.gateway.errors;

import io.netty.resolver.dns.DnsAddressResolverGroup;
import io.netty.resolver.dns.DnsServerAddressStreamProvider;
import java.time.Duration;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import reactor.netty.http.client.HttpClient;

/**
 * Acota la resolucion DNS del cliente con el que el gateway llama a los servicios (AS2-102). Con un
 * contenedor detenido su nombre deja de existir en la red de Docker, y con los tiempos por defecto del
 * resolvedor de Netty (5 s por consulta, varias consultas) el aviso 503 tardaba unos 14 s. Con este
 * limite el nombre que no se resuelve falla en pocos segundos y {@link UpstreamUnavailableHandler}
 * responde 503 enseguida.
 */
public final class FastDnsFailure implements HttpClientCustomizer {

    /** Consultas como maximo por nombre (registro A y AAAA, o un dominio de busqueda). */
    static final int MAX_QUERIES = 2;
    /** Cuanto se recuerda que un nombre no existe: un servicio que vuelve se encuentra enseguida. */
    static final Duration NEGATIVE_CACHE = Duration.ofSeconds(5);

    private final Duration queryTimeout;
    private final DnsServerAddressStreamProvider servers;

    /**
     * @param queryTimeout tiempo maximo de cada consulta DNS
     * @param servers      servidores DNS a consultar; {@code null} usa los del sistema (en Docker, el
     *                     resolvedor interno de la red)
     */
    public FastDnsFailure(Duration queryTimeout, DnsServerAddressStreamProvider servers) {
        this.queryTimeout = queryTimeout;
        this.servers = servers;
    }

    @Override
    public HttpClient customize(HttpClient client) {
        return client.resolver(spec -> {
            spec.queryTimeout(queryTimeout)
                    .maxQueriesPerResolve(MAX_QUERIES)
                    .retryTcpOnTimeout(false)
                    .cacheNegativeTimeToLive(NEGATIVE_CACHE);
            if (servers != null) {
                spec.dnsAddressResolverGroupProvider(builder -> new DnsAddressResolverGroup(
                        builder.nameServerProvider(servers)));
            }
        });
    }
}
