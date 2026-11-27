package academia.gateway.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * DTO de entrada para criação de ficha de treino.
 * Usado em: POST /api/treinos
 */
public record CriarFichaTreinoRequest(

        @NotBlank(message = "alunoId é obrigatório")
        String alunoId,

        @NotBlank(message = "titulo é obrigatório")
        String titulo,

        @NotBlank(message = "objetivo é obrigatório (HIPERTROFIA | EMAGRECIMENTO | CONDICIONAMENTO)")
        String objetivo,

        @NotEmpty(message = "a ficha deve ter pelo menos 1 exercício")
        @Valid
        List<ItemTreinoRequest> itens
) {
    /**
     * DTO aninhado para cada exercício da ficha.
     */
    public record ItemTreinoRequest(

            @NotBlank(message = "nomeExercicio é obrigatório")
            String nomeExercicio,

            String grupoMuscular, // opcional

            @NotNull(message = "series é obrigatório")
            @Positive(message = "series deve ser maior que zero")
            @Max(value=100,message="Informe no máximo 100 séries.") Integer series,

            @NotNull(message = "repeticoes é obrigatório")
            @Positive(message = "repeticoes deve ser maior que zero")
            @Max(value=1000,message="Informe no máximo 1000 repetições.") Integer repeticoes,

            @DecimalMin(value="0",message="A carga não pode ser negativa.")
            @DecimalMax(value="1000",message="Informe a carga em kg, até 1000.") Double cargaSugerida  // opcional — 0 = usar última carga do aluno
    ) {}
}
