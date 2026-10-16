package academia.gateway.dto;

/**
 * DTO com o resultado da autenticação contendo o token JWT gerado (Aula 7).
 */
public record AutenticarResponse(
        String token,
        String tipo,
        long expiraEmMinutos,
        String usuario,
        String mensagem
) {}
