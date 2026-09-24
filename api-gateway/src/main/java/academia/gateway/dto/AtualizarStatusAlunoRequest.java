package academia.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * DTO para atualização do status cadastral do aluno.
 * Usado em: PATCH /api/alunos/{id}/status
 */
public record AtualizarStatusAlunoRequest(

        @NotBlank(message = "status é obrigatório")
        @Pattern(regexp = "ATIVO|INATIVO|INADIMPLENTE", message = "status deve ser ATIVO, INATIVO ou INADIMPLENTE")
        String status
) {}
