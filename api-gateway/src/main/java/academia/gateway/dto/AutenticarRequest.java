package academia.gateway.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO para solicitação de autenticação / login no Gateway.
 * Usado em: POST /api/autenticar
 */
public record AutenticarRequest(
        @NotBlank(message = "username é obrigatório")
        String username,

        @NotBlank(message = "senha é obrigatória")
        String senha
) {}
