package academia.treino;

import academia.grpc.CheckinRequest;
import academia.grpc.CheckinResponse;
import academia.grpc.SolicitacaoTreinoRequest;
import academia.grpc.TreinoResponse;
import academia.grpc.TreinoServiceGrpc;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Service;

@Service
public class TreinoServiceImpl extends TreinoServiceGrpc.TreinoServiceImplBase {

    private final TreinoFacade facade;

    public TreinoServiceImpl(TreinoFacade facade) {
        this.facade = facade;
    }

    @Override
    public void gerarTreino(SolicitacaoTreinoRequest request, StreamObserver<TreinoResponse> responseObserver) {
        responseObserver.onNext(facade.toGrpc(facade.gerarTreino(request.getIdAluno(), request.getObjetivo())));
        responseObserver.onCompleted();
    }

    @Override
    public void realizarCheckin(CheckinRequest request, StreamObserver<CheckinResponse> responseObserver) {
        TreinoFacade.CheckinResult result = facade.realizarCheckin(request.getIdAluno(), request.getIdUnidade());
        responseObserver.onNext(CheckinResponse.newBuilder()
            .setIdCheckin(result.idCheckin())
            .setAutorizado(result.autorizado())
            .setDataHora(result.dataHora())
            .setMensagem(result.mensagem())
            .build());
        responseObserver.onCompleted();
    }
}