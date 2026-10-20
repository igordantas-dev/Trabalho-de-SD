package academia.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * DTO para registro de pagamento de uma mensalidade.
 * Usado em: POST /api/mensalidades/{id}/pagamento
 */
public record RegistrarPagamentoRequest(

        @NotBlank(message = "formaPagamento é obrigatória")
        @Pattern(regexp = "PIX|CARTAO|DINHEIRO|BOLETO", message = "formaPagamento deve ser PIX, CARTAO, DINHEIRO ou BOLETO")
        String formaPagamento
) {}
