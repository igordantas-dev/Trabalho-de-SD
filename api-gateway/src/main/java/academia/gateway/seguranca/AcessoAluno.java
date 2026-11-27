package academia.gateway.seguranca;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;

public final class AcessoAluno {
    private AcessoAluno() { }
    public static void verificar(String alunoId) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken token)) throw new AccessDeniedException("Sessao obrigatoria");
        if (token.getAuthorities().stream().anyMatch(role -> role.getAuthority().equals("ROLE_ADMIN"))) return;
        if (!alunoId.equals(token.getToken().getClaimAsString("aluno_id")))
            throw new AccessDeniedException("Aluno nao pode acessar dados de outra pessoa");
    }
}
