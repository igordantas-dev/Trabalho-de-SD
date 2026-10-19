package academia.gateway.seguranca;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Configuração de segurança do API Gateway com Spring Security e OAuth2 Resource Server.
 * Baseado no modelo ensinado na Aula 7:
 *   - Validação de tokens JWT assinados via algoritmo HS256.
 *   - API totalmente Stateless (sem sessões HTTP em memória).
 *   - Rotas /api/** protegidas (retornam 401 Unauthorized se token ausente ou inválido).
 *   - Rotas públicas liberadas para autenticação, documentação Swagger e frontend web.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${jwt.secret}")
    private String segredoJwt;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 1. Endpoints de autenticação pública
                        .requestMatchers(
                                "/api/autenticar",
                                "/api/gerarToken"
                        ).permitAll()

                        // 2. Swagger / OpenAPI 3
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/v3/api-docs"
                        ).permitAll()

                        // 3. Frontend Web SPA e arquivos estáticos
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/css/**",
                                "/js/**",
                                "/assets/**",
                                "/favicon.ico"
                        ).permitAll()

                        // 4. Qualquer outra rota da API de negócio exige token JWT válido
                        .requestMatchers("/api/**").authenticated()

                        // 5. Demais recursos
                        .anyRequest().permitAll()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.decoder(jwtDecoder()))
                        .authenticationEntryPoint(customAuthenticationEntryPoint())
                );

        return http.build();
    }

    /**
     * Decoder do Spring Security configurado com a chave secreta HMAC-SHA256 (HS256).
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        SecretKeySpec secretKey = new SecretKeySpec(
                segredoJwt.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );
        return NimbusJwtDecoder.withSecretKey(secretKey).build();
    }

    /**
     * Resposta clara em JSON com status 401 e cabeçalho WWW-Authenticate padrão (Aula 7).
     */
    @Bean
    public AuthenticationEntryPoint customAuthenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("WWW-Authenticate", "Bearer error=\"invalid_token\", error_description=\"" + authException.getMessage() + "\"");
            response.getWriter().write("""
                    {"erro":"UNAUTHORIZED","mensagem":"Token ausente, invalido ou expirado. Envie 'Authorization: Bearer <token>'"}
                    """.trim());
        };
    }
}
