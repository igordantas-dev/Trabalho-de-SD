package academia.gateway.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO para solicitação de geração inteligente de treino com Inteligência Artificial.
 * Usado em: POST /api/gerar-treino-ia
 */
public record GerarTreinoIaRequest(

        @NotBlank(message = "alunoId é obrigatório")
        String alunoId,

        @NotBlank(message = "objetivo é obrigatório (HIPERTROFIA | EMAGRECIMENTO | CONDICIONAMENTO | FORCA)")
        String objetivo,

        String nivel,        // INICIANTE | INTERMEDIARIO | AVANCADO (padrão: INTERMEDIARIO)

        String focoMuscular, // ex: Peito e Tríceps, Costas e Bíceps, Pernas, Full Body

        String observacoes   // ex: evitar exercícios com impacto, foco em postura
) {}
