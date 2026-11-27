package academia.gateway.dto;
public record AutenticarResponse(String token, String tipo, long expiraEmMinutos,
    String usuario, String perfil, String alunoId, String mensagem) { }
