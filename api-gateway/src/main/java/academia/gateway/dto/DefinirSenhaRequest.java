package academia.gateway.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record DefinirSenhaRequest(@NotBlank(message="Informe a senha.") @Size(min=8,max=128,message="A senha deve ter entre 8 e 128 caracteres.") String senha) { }
