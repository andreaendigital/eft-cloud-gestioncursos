package com.sistema.cursos.bff.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuración de seguridad del BFF Service.
 *
 * <p>Establece Spring Security como OAuth2 Resource Server:
 * <ul>
 *   <li>Valida tokens JWT emitidos por Azure AD usando el JWKS del emisor.</li>
 *   <li>Política de sesión STATELESS: no se crean HttpSession.</li>
 *   <li>CSRF deshabilitado: adecuado para APIs REST consumidas por otros servicios.</li>
 *   <li>Todos los endpoints protegidos requieren token JWT válido.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Define la cadena de filtros de seguridad HTTP.
     *
     * @param http objeto de configuración de seguridad provisto por Spring
     * @return la cadena de filtros configurada y construida
     * @throws Exception si ocurre un error durante la configuración
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Deshabilitar CSRF: no aplica en APIs REST stateless
            .csrf(AbstractHttpConfigurer::disable)

            // Política stateless: no se usa HttpSession ni cookies de sesión
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // Reglas de autorización por ruta
            .authorizeHttpRequests(auth -> auth
                // Endpoints de inscripciones y cursos requieren JWT válido
                .requestMatchers("/api/v1/inscripciones/**").authenticated()
                .requestMatchers("/api/v1/cursos/**").authenticated()
                // Actuator health check público (útil para healthcheck en Docker/EC2)
                .requestMatchers("/actuator/health").permitAll()
                // Cualquier otra ruta también requiere autenticación
                .anyRequest().authenticated()
            )

            // Configurar como OAuth2 Resource Server con validación JWT
            // Spring descarga automáticamente el JWKS desde el issuer-uri
            .oauth2ResourceServer(oauth2 ->
                oauth2.jwt(Customizer.withDefaults())
            );

        return http.build();
    }
}
