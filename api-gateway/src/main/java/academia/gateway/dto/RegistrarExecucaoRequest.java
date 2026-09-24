package academia.gateway.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

/**
 * DTO para registrar a execução de um treino com as cargas reais utilizadas.
 * Usado em: POST /api/treinos/{id}/execucao
 */
public record RegistrarExecucaoRequest(

        @NotEmpty(message = "itens não pode ser vazio")
        @Valid
        List<ExecucaoItemDto> itens
) {
    public record ExecucaoItemDto(
            @NotBlank(message = "itemId é obrigatório")
            String itemId,

            @NotNull(message = "cargaUsada é obrigatória")
            @PositiveOrZero(message = "cargaUsada não pode ser negativa")
            Double cargaUsada,

            Boolean concluido
    ) {}
}
