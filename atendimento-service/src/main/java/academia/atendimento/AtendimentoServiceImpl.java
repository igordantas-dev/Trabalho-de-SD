package academia.atendimento;

import academia.grpc.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

public class AtendimentoServiceImpl extends TreinoServiceGrpc.TreinoServiceImplBase {

    private static final String TREINO_HOST = System.getenv().getOrDefault("TREINO_HOST", "localhost");
    private static final int TREINO_PORTA = 9091;

    private final TreinoServiceGrpc.TreinoServiceBlockingStub treinoClient;

    public AtendimentoServiceImpl() {
        ManagedChannel channel = ManagedChannelBuilder.forAddress(TREINO_HOST, TREINO_PORTA)
                .usePlaintext()
                .build();
        this.treinoClient = TreinoServiceGrpc.newBlockingStub(channel);
    }

    @Override
    public void gerarTreino(SolicitacaoTreinoRequest request, StreamObserver<TreinoResponse> responseObserver) {
        System.out.println("[AtendimentoService] Encaminhando solicitação de treino para TreinoService...");
        TreinoResponse response = treinoClient.gerarTreino(request);
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void realizarCheckin(CheckinRequest request, StreamObserver<CheckinResponse> responseObserver) {
        System.out.println("[AtendimentoService] Encaminhando solicitação de check-in para TreinoService...");
        CheckinResponse response = treinoClient.realizarCheckin(request);
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}