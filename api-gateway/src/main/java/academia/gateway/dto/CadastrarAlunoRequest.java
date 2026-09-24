package academia.gateway.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * DTO de entrada para cadastro de aluno.
 * Validações com Bean Validation — padrão da Aula 6.
 *
 * Usado em: POST /api/alunos
 */
public record CadastrarAlunoRequest(

        @NotBlank(message = "nome é obrigatório")
        String nome,

        @NotBlank(message = "cpf é obrigatório")
        String cpf,

        @NotBlank(message = "email é obrigatório")
        @Email(message = "email deve ter formato válido")
        String email,

        String telefone,  // opcional

        @Positive(message = "peso deve ser maior que zero")
        Double peso,      // opcional — pode ser null

        @Positive(message = "altura deve ser maior que zero")
        Double altura     // opcional — pode ser null
) {}
