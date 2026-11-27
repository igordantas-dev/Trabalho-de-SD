package academia.gateway.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
public record AutenticarRequest(
    @NotBlank(message="Informe o e-mail.") @Size(max=255,message="Use no máximo 255 caracteres.") String username,
    @NotBlank(message="Informe a senha.") @Size(max=128,message="Use no máximo 128 caracteres.") String senha,
    @NotBlank(message="Selecione o portal.") @Pattern(regexp="ADMIN|ALUNO", message="Selecione o portal correto.") String perfil
) { }
