package academia.treino;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TreinoController {

    private final TreinoFacade facade;

    public TreinoController(TreinoFacade facade) {
        this.facade = facade;
    }

    @PostMapping("/treinos")
    public TreinoFacade.TreinoResult gerarTreino(@Valid @RequestBody TreinoRequest request) {
        return facade.gerarTreino(request.idAluno(), request.objetivo());
    }

    @PostMapping("/checkins")
    public TreinoFacade.CheckinResult realizarCheckin(@Valid @RequestBody CheckinRequest request) {
        return facade.realizarCheckin(request.idAluno(), request.idUnidade());
    }

    public record TreinoRequest(
            @NotBlank String idAluno,
            String nomeAluno,
            @NotBlank String objetivo,
            String nivelExperiencia) {
    }

    public record CheckinRequest(
            @NotBlank String idAluno,
            @NotBlank String idUnidade) {
    }
}