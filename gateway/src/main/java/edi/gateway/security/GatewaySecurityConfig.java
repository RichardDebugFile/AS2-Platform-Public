package edi.gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher.MatchResult;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import java.util.Set;

/**
 * Seguridad en el borde. El gateway es el unico punto expuesto: todo lo que no sea una ruta publica
 * exige un JWT valido (firma RS256 contra el JWKS de auth-service y caducidad).
 *
 * <p>Se valida aqui y no en cada servicio porque los servicios se llaman entre si por la red
 * interna (que no se publica); exigir el JWT dentro de cada uno queda como mejora de defensa en
 * profundidad (ver backlog futuro).</p>
 *
 * <p>Hay dos cadenas a proposito. La publica <b>no inspecciona el token</b>: si lo hiciera, una
 * cookie {@code access_token} caducada en {@code POST /auth/refresh} daria 401 aqui, antes de llegar
 * a auth-service, y la sesion no se podria renovar nunca.</p>
 */
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    /**
     * Rutas que por su naturaleza no llevan sesion de usuario. Lista cerrada (RN-08): anadir una
     * ruta exige justificarla en el PR.
     */
    static final String[] PUBLIC_PATHS = {
        "/auth/login", "/auth/refresh", "/auth/logout", "/auth/mfa/**", // obtener o renovar la sesion
        "/.well-known/**", // JWKS: publico por definicion
        "/actuator/health", "/actuator/health/**",
        "/as2/receive", "/as2/receive/**", // los socios AS2 se autentican con S/MIME, no con JWT
        "/internal/as2/mdn-callback" // MDN asincrono que envian los socios
    };
    // /auth/register NO es publico: crea un usuario y auth-service exige ADMIN.

    /** APIs de configuracion que solo usa una pantalla de administracion. */
    static final String[] ADMIN_PATHS = {"/api/smtp-config", "/api/smtp-config/**"};

    /** Borrado definitivo de un socio. */
    static final String PARTNER_PURGE = "/api/partners/*/purge";

    /** Enlace con la carpeta compartida y activacion de produccion: cambian lo que ve el ERP (HU-09). */
    static final String[] PARTNER_ERP_CHANGES = {"/api/partners/*/folders/link", "/api/partners/*/production"};

    /** Eliminar un certificado de un socio (HU-11): solo ADMIN; desactivarlo lo puede hacer OPERATOR. */
    static final String PARTNER_CERTIFICATE = "/api/partners/*/certificates/*";

    /** Roles que pueden modificar datos. AUDITOR es de solo lectura. */
    static final String ADMIN = "ADMIN";
    static final String[] ROLES_QUE_EDITAN = {ADMIN, "OPERATOR"};

    /** Metodos que no modifican nada: no necesitan token CSRF. */
    static final Set<HttpMethod> SAFE_METHODS = Set.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS, HttpMethod.TRACE);

    /**
     * CSRF solo donde hay riesgo: peticiones que modifican y que se autentican con la cookie de
     * sesion, que el navegador adjunta por su cuenta aunque la pagina sea de otro sitio. Las que
     * traen {@code Authorization} (scripts, clientes API) no lo necesitan: esa cabecera no la puede
     * forjar otro sitio. Sin cookie no hay sesion que suplantar (y la peticion recibe su 401).
     */
    static final ServerWebExchangeMatcher COOKIE_SESSION_MUTATION = exchange -> {
        var req = exchange.getRequest();
        boolean requiere = !SAFE_METHODS.contains(req.getMethod())
                && !req.getHeaders().containsHeader("Authorization")
                && req.getCookies().containsKey(CookieOrHeaderBearerTokenConverter.ACCESS_TOKEN_COOKIE);
        return requiere ? MatchResult.match() : MatchResult.notMatch();
    };

    /**
     * Doble envio con el token de auth-service: la cookie legible {@code XSRF-TOKEN} debe volver en
     * la cabecera {@code X-XSRF-TOKEN}. Otro sitio no puede leer la cookie, asi que no puede forjar
     * la cabecera. El gateway solo compara: no emite ni cambia la cookie (la emite auth-service).
     *
     * <p>Hace falta aqui, en el borde, porque el gateway convierte la cookie de sesion en
     * {@code Authorization: Bearer} para los servicios: estos ya no ven una peticion "con cookie" y
     * no podrian aplicar la proteccion por su cuenta.</p>
     */
    static void csrfConCookieDeSesion(ServerHttpSecurity.CsrfSpec csrf) {
        csrf.csrfTokenRepository(CookieServerCsrfTokenRepository.withHttpOnlyFalse())
                // Token tal cual (sin enmascarar): es el que la consola lee de la cookie y reenvia.
                .csrfTokenRequestHandler(new ServerCsrfTokenRequestAttributeHandler())
                .requireCsrfProtectionMatcher(COOKIE_SESSION_MUTATION);
    }

    static ServerWebExchangeMatcher publicMatcher() {
        return ServerWebExchangeMatchers.matchers(
                ServerWebExchangeMatchers.pathMatchers(HttpMethod.OPTIONS, "/**"), // preflight CORS
                ServerWebExchangeMatchers.pathMatchers(PUBLIC_PATHS));
    }

    @Bean
    @Order(1)
    SecurityWebFilterChain publicChain(ServerHttpSecurity http) {
        return http.securityMatcher(publicMatcher())
                // Refresh y logout llegan con la cookie de sesion: tambien exigen el token CSRF.
                .csrf(GatewaySecurityConfig::csrfConCookieDeSesion)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .authorizeExchange(ex -> ex.anyExchange().permitAll())
                .build();
    }

    @Bean
    @Order(2)
    SecurityWebFilterChain protectedChain(ServerHttpSecurity http, ReactiveJwtDecoder jwtDecoder) {
        return http.csrf(GatewaySecurityConfig::csrfConCookieDeSesion)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                // Sin sesion de servidor: cada peticion trae su token.
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                // Modificar algo en /api/** exige ADMIN u OPERATOR; AUDITOR solo consulta.
                .authorizeExchange(ex -> ex.pathMatchers(ADMIN_PATHS)
                        .hasRole(ADMIN)
                        // Borrado definitivo de un socio (y sus configuraciones): solo ADMIN.
                        .pathMatchers(HttpMethod.DELETE, PARTNER_PURGE)
                        .hasRole(ADMIN)
                        // Eliminar un certificado de un socio: solo ADMIN (HU-11).
                        .pathMatchers(HttpMethod.DELETE, PARTNER_CERTIFICATE)
                        .hasRole(ADMIN)
                        // Enlazar carpetas con el ERP y pasar un socio a produccion: solo ADMIN.
                        .pathMatchers(HttpMethod.POST, PARTNER_ERP_CHANGES)
                        .hasRole(ADMIN)
                        .pathMatchers(HttpMethod.POST, "/api/**")
                        .hasAnyRole(ROLES_QUE_EDITAN)
                        .pathMatchers(HttpMethod.PUT, "/api/**")
                        .hasAnyRole(ROLES_QUE_EDITAN)
                        .pathMatchers(HttpMethod.PATCH, "/api/**")
                        .hasAnyRole(ROLES_QUE_EDITAN)
                        .pathMatchers(HttpMethod.DELETE, "/api/**")
                        .hasAnyRole(ROLES_QUE_EDITAN)
                        .anyExchange()
                        .authenticated())
                .oauth2ResourceServer(oauth -> oauth.bearerTokenConverter(new CookieOrHeaderBearerTokenConverter())
                        .jwt(jwt -> jwt.jwtDecoder(jwtDecoder).jwtAuthenticationConverter(rolesFromToken())))
                .build();
    }

    /**
     * El claim {@code roles} del JWT ({@code ["ADMIN"]}, emitido por auth-service) pasa a autoridades
     * {@code ROLE_ADMIN}, necesarias para {@code hasRole}.
     */
    static ReactiveJwtAuthenticationConverterAdapter rolesFromToken() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter conv = new JwtAuthenticationConverter();
        conv.setJwtGrantedAuthoritiesConverter(roles);
        return new ReactiveJwtAuthenticationConverterAdapter(conv);
    }

    /**
     * Valida firma y caducidad contra el JWKS de auth-service. No se valida el issuer: cambia por
     * entorno (local, Docker, servidor) y la seguridad descansa en la firma RSA.
     */
    @Bean
    ReactiveJwtDecoder jwtDecoder(
            @Value("${gateway.security.jwks-uri:http://localhost:8083/.well-known/jwks.json}") String jwksUri) {
        return NimbusReactiveJwtDecoder.withJwkSetUri(jwksUri).build();
    }
}
