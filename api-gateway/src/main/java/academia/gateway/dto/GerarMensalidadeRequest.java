package academia.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * DTO para geração de mensalidade de um aluno.
 * Usado em: POST /api/mensalidades
 */
public record GerarMensalidadeRequest(

        @NotBlank(message = "alunoId é obrigatório")
        String alunoId,

        @NotNull(message = "valor é obrigatório")
        @Positive(message = "valor deve ser positivo")
        Double valor,

        @NotBlank(message = "dataVencimento é obrigatória (formato YYYY-MM-DD)")
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "dataVencimento deve estar no formato YYYY-MM-DD")
        String dataVencimento,

        String descricao // opcional
) {}
