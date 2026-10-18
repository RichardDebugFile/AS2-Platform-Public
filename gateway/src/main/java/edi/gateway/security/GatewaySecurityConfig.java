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
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;

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

    /** Roles que pueden modificar datos. AUDITOR es de solo lectura. */
    static final String[] ROLES_QUE_EDITAN = {"ADMIN", "OPERATOR"};

    static ServerWebExchangeMatcher publicMatcher() {
        return ServerWebExchangeMatchers.matchers(
                ServerWebExchangeMatchers.pathMatchers(HttpMethod.OPTIONS, "/**"), // preflight CORS
                ServerWebExchangeMatchers.pathMatchers(PUBLIC_PATHS));
    }

    @Bean
    @Order(1)
    SecurityWebFilterChain publicChain(ServerHttpSecurity http) {
        return http.securityMatcher(publicMatcher())
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .authorizeExchange(ex -> ex.anyExchange().permitAll())
                .build();
    }

    @Bean
    @Order(2)
    SecurityWebFilterChain protectedChain(ServerHttpSecurity http, ReactiveJwtDecoder jwtDecoder) {
        return http
                // La proteccion CSRF de la sesion en cookies la aplica auth-service (doble cookie
                // XSRF-TOKEN); el gateway no guarda sesion y reenvia la cabecera tal cual.
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                // Sin sesion de servidor: cada peticion trae su token.
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                // Modificar algo en /api/** exige ADMIN u OPERATOR; AUDITOR solo consulta.
                .authorizeExchange(ex -> ex.pathMatchers(ADMIN_PATHS)
                        .hasRole("ADMIN")
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
