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
@Tag(name = "Autenticação", description = "Emissão e renovação de token de acesso")
public class AuthController {

    @Value("${jwt.secret}")
    private String segredoJwt;

    @Value("${jwt.expiration-minutes:30}")
    private long expiracaoMinutos;

    @PostMapping("/autenticar")
    @Operation(summary = "Autenticar", description = "Recebe credenciais e retorna o token de acesso para uso nas demais rotas.")
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

    @GetMapping("/gerarToken")
    @Operation(summary = "Gerar token de demonstração", description = "Emite um token de acesso de demonstração com usuário padrão.")
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
