package com.sistema.cursos.bff.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

/**
 * Configuración de seguridad del BFF Service.
 *
 * <p>Configura Spring Security como OAuth2 Resource Server para validar
 * tokens JWT emitidos por Azure AD con tres niveles de verificación:
 * <ol>
 *   <li><b>Firma RSA</b>: descargando las claves públicas desde {@code AZURE_AD_JWKS_URI}.</li>
 *   <li><b>Issuer</b>: el claim {@code iss} debe coincidir con {@code AZURE_AD_ISSUER_URI}.</li>
 *   <li><b>Audience</b>: el claim {@code aud} debe contener {@code AZURE_CLIENT_ID},
 *       garantizando que el token fue emitido específicamente para este BFF.</li>
 * </ol>
 *
 * <p>{@code AZURE_CLIENT_SECRET} y {@code AZURE_TENANT_ID} NO son necesarios aquí:
 * el BFF solo verifica tokens, nunca los solicita.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Application (client) ID registrado en Azure AD para este BFF.
     * Se usa para validar el claim {@code aud} del JWT entrante.
     */
    @Value("${azure.ad.client-id}")
    private String clientId;

    /**
     * URI del emisor JWT de Azure AD (para validar el claim {@code iss}).
     */
    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    /**
     * URI del endpoint JWKS de Azure AD (para descargar claves públicas RSA).
     */
    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwksUri;

    /**
     * Define la cadena de filtros de seguridad HTTP.
     *
     * @param http objeto de configuración de seguridad provisto por Spring
     * @return la cadena de filtros configurada
     * @throws Exception si ocurre un error durante la configuración
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Deshabilitar CSRF: no aplica en APIs REST stateless
            .csrf(AbstractHttpConfigurer::disable)

            // Política stateless: sin HttpSession ni cookies
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // Reglas de autorización por ruta
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/inscripciones/**").authenticated()
                .requestMatchers("/api/v1/cursos/**").authenticated()
                // Health check público para Docker/EC2
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated()
            )

            // OAuth2 Resource Server con validación JWT usando el decoder personalizado
            .oauth2ResourceServer(oauth2 ->
                oauth2.jwt(jwt -> jwt.decoder(jwtDecoder()))
            );

        return http.build();
    }

    /**
     * Configura el {@link JwtDecoder} con validación de firma, issuer y audience.
     *
     * <p>La validación de audience ({@code aud == AZURE_CLIENT_ID}) es crítica:
     * sin ella, un token válido emitido para otra aplicación del mismo tenant
     * podría usarse para acceder a este BFF.
     *
     * @return decoder JWT con validaciones de issuer y audience encadenadas
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        // Construir el decoder usando directamente la URI del JWKS de Azure AD
        NimbusJwtDecoder decoder = NimbusJwtDecoder
            .withJwkSetUri(jwksUri)
            .build();

        // Validador de issuer estándar (verifica claim "iss")
        OAuth2TokenValidator<Jwt> issuerValidator =
            JwtValidators.createDefaultWithIssuer(issuerUri);

        // Validador de audience: el claim "aud" debe contener el clientId del BFF
        // Esto garantiza que el token fue emitido específicamente para esta aplicación
        OAuth2TokenValidator<Jwt> audienceValidator =
            new JwtClaimValidator<List<String>>(
                JwtClaimNames.AUD,
                aud -> aud != null && aud.contains(clientId)
            );

        // Encadenar ambos validadores
        decoder.setJwtValidator(
            new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator)
        );

        return decoder;
    }
}
