package academia.atendimento;

import academia.grpc.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

public class ClienteAtendimentoGrpc {

    private static final String HOST = System.getenv().getOrDefault("ATENDIMENTO_HOST", "localhost");
    private static final int PORTA = 9090;

    public static void main(String[] args) {
        ManagedChannel channel = ManagedChannelBuilder.forAddress(HOST, PORTA)
                .usePlaintext()
                .build();

        TreinoServiceGrpc.TreinoServiceBlockingStub stub = TreinoServiceGrpc.newBlockingStub(channel);

        System.out.println("=== TESTE 1: Check-in de Aluno Ativo (ALUNO-101) ===");
        CheckinResponse resp1 = stub.realizarCheckin(CheckinRequest.newBuilder()
                .setIdAluno("ALUNO-101")
                .setIdUnidade("UNIDADE-CENTRO")
                .build());
        System.out.println("Status: " + resp1.getAutorizado());
        System.out.println("Mensagem: " + resp1.getMensagem());

        System.out.println("\n=== TESTE 2: Geração de Treino para Aluno Inadimplente (ALUNO-102) ===");
        TreinoResponse resp2 = stub.gerarTreino(SolicitacaoTreinoRequest.newBuilder()
                .setIdAluno("ALUNO-102")
                .setNomeAluno("Carlos")
                .setObjetivo("HIPERTROFIA")
                .setNivelExperiencia("INTERMEDIARIO")
                .build());
        System.out.println("Status Matrícula: " + resp2.getStatusMatricula());
        System.out.println("Mensagem: " + resp2.getMensagem());

        channel.shutdown();
    }
}