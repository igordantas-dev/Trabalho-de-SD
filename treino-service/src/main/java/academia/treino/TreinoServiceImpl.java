package academia.treino;

import academia.grpc.*;
import io.grpc.stub.StreamObserver;
import java.time.LocalDateTime;
import java.util.*;

public class TreinoServiceImpl extends TreinoServiceGrpc.TreinoServiceImplBase {

    // Simulação de banco de dados em memória
    private static final Map<String, String> MATRICULAS = Map.of(
        "ALUNO-101", "ATIVA",
        "ALUNO-102", "INADIMPLENTE",
        "ALUNO-103", "ATIVA"
    );

    @Override
    public void gerarTreino(SolicitacaoTreinoRequest request, StreamObserver<TreinoResponse> responseObserver) {
        String status = MATRICULAS.getOrDefault(request.getIdAluno(), "INEXISTENTE");

        TreinoResponse.Builder responseBuilder = TreinoResponse.newBuilder()
                .setIdTreino(UUID.randomUUID().toString())
                .setIdAluno(request.getIdAluno())
                .setStatusMatricula(status);

        if (!"ATIVA".equals(status)) {
            responseBuilder.setMensagem("Geração de treino negada: Matrícula " + status)
                           .build();
        } else {
            List<ExercicioProto> exercicios = new ArrayList<>();
            if ("HIPERTROFIA".equalsIgnoreCase(request.getObjetivo())) {
                exercicios.add(ExercicioProto.newBuilder().setNome("Supino Reto").setSeries(4).setRepeticoes(10).setDescanso("60s").build());
                exercicios.add(ExercicioProto.newBuilder().setNome("Agachamento Livre").setSeries(4).setRepeticoes(8).setDescanso("90s").build());
            } else {
                exercicios.add(ExercicioProto.newBuilder().setNome("Corrida Esteira").setSeries(1).setRepeticoes(30).setDescanso("0s").build());
                exercicios.add(ExercicioProto.newBuilder().setNome("Polichinelos").setSeries(3).setRepeticoes(20).setDescanso("30s").build());
            }

            responseBuilder.addAllExercicios(exercicios)
                           .setMensagem("Treino gerado com sucesso para o objetivo: " + request.getObjetivo());
        }

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void realizarCheckin(CheckinRequest request, StreamObserver<CheckinResponse> responseObserver) {
        String status = MATRICULAS.getOrDefault(request.getIdAluno(), "INEXISTENTE");
        boolean autorizado = "ATIVA".equals(status);

        CheckinResponse response = CheckinResponse.newBuilder()
                .setIdCheckin(UUID.randomUUID().toString())
                .setAutorizado(autorizado)
                .setDataHora(LocalDateTime.now().toString())
                .setMensagem(autorizado ? "Check-in realizado com sucesso na unidade " + request.getIdUnidade()
                                        : "Check-in bloqueado. Situação cadastral: " + status)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}