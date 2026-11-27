package academia.gateway.controller;

import academia.gateway.dto.AutenticarRequest;
import academia.gateway.dto.AutenticarResponse;
import academia.gateway.seguranca.GeradorTokenJwt;
import academia.grpc.AlunoServiceGrpc;
import academia.grpc.AutenticarAlunoRequest;
import academia.seguranca.Senhas;
import io.grpc.Status;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final AlunoServiceGrpc.AlunoServiceBlockingStub alunoStub;
    public AuthController(AlunoServiceGrpc.AlunoServiceBlockingStub alunoStub) { this.alunoStub = alunoStub; }
    @Value("${jwt.secret}") private String segredoJwt;
    @Value("${jwt.expiration-minutes:30}") private long expiracaoMinutos;
    @Value("${ADMIN_LOGIN:}") private String adminLogin;
    @Value("${ADMIN_PASSWORD_HASH:}") private String adminHash;
    @Value("${grpc.deadline-seconds:5}") private long deadlineSegundos;

    @PostMapping("/autenticar")
    public ResponseEntity<AutenticarResponse> autenticar(@Valid @RequestBody AutenticarRequest request) {
        String usuario = request.username().trim().toLowerCase(java.util.Locale.ROOT);
        String alunoId = "";
        if ("ADMIN".equals(request.perfil())) {
            if (adminLogin.isBlank() || adminHash.isBlank())
                throw Status.UNAVAILABLE.withDescription("Acesso administrativo ainda nao configurado.").asRuntimeException();
            boolean senhaValida = Senhas.verificar(request.senha(), adminHash);
            if (!adminLogin.equalsIgnoreCase(usuario) || !senhaValida)
                throw Status.UNAUTHENTICATED.withDescription("E-mail ou senha incorretos.").asRuntimeException();
        } else {
            var aluno = alunoStub.withDeadlineAfter(deadlineSegundos, TimeUnit.SECONDS).autenticarAluno(
                AutenticarAlunoRequest.newBuilder().setEmail(usuario).setSenha(request.senha()).build());
            alunoId = aluno.getId();
        }
        String token = GeradorTokenJwt.gerarToken(usuario, request.perfil(), alunoId, segredoJwt, expiracaoMinutos);
        return ResponseEntity.ok(new AutenticarResponse(token,"Bearer",expiracaoMinutos,usuario,request.perfil(),alunoId,"Login realizado com sucesso."));
    }

    @GetMapping("/sessao")
    public Map<String,String> sessao(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("usuario",jwt.getSubject(),"perfil",jwt.getClaimAsString("perfil"),
            "alunoId",jwt.getClaimAsString("aluno_id") == null ? "" : jwt.getClaimAsString("aluno_id"));
    }
}
