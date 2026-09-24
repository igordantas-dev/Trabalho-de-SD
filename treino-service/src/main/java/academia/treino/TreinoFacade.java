package academia.treino;

import academia.grpc.ExercicioProto;
import academia.grpc.TreinoResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class TreinoFacade {

    private final MatriculaRepository matriculaRepository;

    public TreinoFacade(MatriculaRepository matriculaRepository) {
        this.matriculaRepository = matriculaRepository;
    }

    public TreinoResult gerarTreino(String idAluno, String objetivo) {
        String status = statusDaMatricula(idAluno);
        List<ExercicioResult> exercicios = new ArrayList<>();
        String mensagem;

        if (!StatusMatricula.ATIVA.name().equals(status)) {
            mensagem = "Geração de treino negada: Matrícula " + status;
        } else {
            if ("HIPERTROFIA".equalsIgnoreCase(objetivo)) {
                exercicios.add(new ExercicioResult("Supino Reto", 4, 10, "60s"));
                exercicios.add(new ExercicioResult("Agachamento Livre", 4, 8, "90s"));
            } else {
                exercicios.add(new ExercicioResult("Corrida Esteira", 1, 30, "0s"));
                exercicios.add(new ExercicioResult("Polichinelos", 3, 20, "30s"));
            }
            mensagem = "Treino gerado com sucesso para o objetivo: " + objetivo;
        }

        return new TreinoResult(UUID.randomUUID().toString(), idAluno, status, exercicios, mensagem);
    }

    public CheckinResult realizarCheckin(String idAluno, String idUnidade) {
        String status = statusDaMatricula(idAluno);
        boolean autorizado = StatusMatricula.ATIVA.name().equals(status);
        String mensagem = autorizado
                ? "Check-in realizado com sucesso na unidade " + idUnidade
                : "Check-in bloqueado. Situação cadastral: " + status;

        return new CheckinResult(UUID.randomUUID().toString(), autorizado, LocalDateTime.now().toString(), mensagem);
    }

    private String statusDaMatricula(String idAluno) {
        return matriculaRepository.findById(idAluno)
                .map(matricula -> matricula.getStatus().name())
                .orElse("INEXISTENTE");
    }

    public record ExercicioResult(String nome, int series, int repeticoes, String descanso) {
    }

    public record TreinoResult(String idTreino, String idAluno, String statusMatricula,
                               List<ExercicioResult> exercicios, String mensagem) {
    }

    public record CheckinResult(String idCheckin, boolean autorizado, String dataHora, String mensagem) {
    }

    public TreinoResponse toGrpc(TreinoResult result) {
        TreinoResponse.Builder builder = TreinoResponse.newBuilder()
                .setIdTreino(result.idTreino())
                .setIdAluno(result.idAluno())
                .setStatusMatricula(result.statusMatricula())
                .setMensagem(result.mensagem());

        result.exercicios().forEach(exercicio -> builder.addExercicios(ExercicioProto.newBuilder()
                .setNome(exercicio.nome())
                .setSeries(exercicio.series())
                .setRepeticoes(exercicio.repeticoes())
                .setDescanso(exercicio.descanso())
                .build()));

        return builder.build();
    }
}