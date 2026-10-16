package academia.gateway.controller;

import academia.gateway.dto.AutenticarRequest;
import academia.gateway.dto.AutenticarResponse;
import academia.gateway.seguranca.GeradorTokenJwt;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller REST para autenticação e emissão de tokens JWT.
 * O token gerado deve ser enviado nas rotas protegidas através do cabeçalho:
 *   Authorization: Bearer <token>
 */
@RestController
@RequestMapping("/api")
@Tag(name = "1. Autenticação", description = "Endpoints para emissão de token JWT (padrão Aula 7)")
public class AuthController {

    @Value("${jwt.secret}")
    private String segredoJwt;

    @Value("${jwt.expiration-minutes:30}")
    private long expiracaoMinutos;

    @PostMapping({"/autenticar", "/realizar-login"})
    @Operation(summary = "Realizar login e emitir token JWT", description = "Recebe credenciais e retorna o Bearer token JWT para autorização nas demais rotas.")
    public ResponseEntity<AutenticarResponse> autenticar(@Valid @RequestBody AutenticarRequest request) {
        String token = GeradorTokenJwt.gerarToken(request.username(), segredoJwt, expiracaoMinutos);
        return ResponseEntity.ok(new AutenticarResponse(
                token,
                "Bearer",
                expiracaoMinutos,
                request.username(),
                "Autenticação realizada com sucesso. Use o token no cabeçalho Authorization: Bearer <token>"
        ));
    }

    @GetMapping("/gerar-token")
    @Operation(summary = "Gerar token JWT rápido (laboratório)", description = "Emite um token JWT de demonstração com sujeito 'aluno' idêntico ao comando do terminal da Aula 7.")
    public ResponseEntity<AutenticarResponse> gerarTokenRapido() {
        String token = GeradorTokenJwt.gerarToken("aluno", segredoJwt, expiracaoMinutos);
        return ResponseEntity.ok(new AutenticarResponse(
                token,
                "Bearer",
                expiracaoMinutos,
                "aluno",
                "Token de laboratório gerado com sucesso."
        ));
    }
}
