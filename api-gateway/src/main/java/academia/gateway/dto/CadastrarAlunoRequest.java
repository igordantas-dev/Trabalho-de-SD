package academia.gateway.dto;
import jakarta.validation.constraints.*;

public record CadastrarAlunoRequest(
    @NotBlank(message="Informe o nome.") @Size(max=255,message="Use no máximo 255 caracteres.") String nome,
    @NotBlank(message="Informe o CPF.") @Size(max=30,message="Use no máximo 30 caracteres.") String cpf,
    @NotBlank(message="Informe o e-mail.") @Email(message="Informe um e-mail valido.") @Size(max=255,message="Use no máximo 255 caracteres.") String email,
    @Size(max=255,message="Use no máximo 255 caracteres.") String telefone,
    @DecimalMin(value="1",message="Informe o peso em kg, entre 1 e 500.")
    @DecimalMax(value="500",message="Informe o peso em kg, entre 1 e 500.") Double peso,
    @DecimalMin(value="0.5",message="Informe a altura em metros. Exemplo: 1,70.")
    @DecimalMax(value="2.5",message="Informe a altura em metros: 1,70 em vez de 170.") Double altura,
    @NotBlank(message="Informe a senha.") @Size(min=8,max=128,message="A senha inicial deve ter entre 8 e 128 caracteres.") String senha
) { }
