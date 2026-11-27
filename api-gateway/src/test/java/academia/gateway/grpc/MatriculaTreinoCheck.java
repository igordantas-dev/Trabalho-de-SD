package academia.gateway.grpc;

import academia.gateway.controller.TreinoController;
import academia.gateway.dto.RegistrarExecucaoRequest;
import academia.gateway.servico.MatriculaTreinoService;
import academia.grpc.*;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Exercita o controller real usando servidores gRPC locais, sem banco. */
public final class MatriculaTreinoCheck {
    public static void main(String[] args) throws Exception {
        var status = new AtomicReference<>("INATIVO");
        var escritas = new AtomicInteger();
        Server server = ServerBuilder.forPort(0)
            .addService(new AlunoServiceGrpc.AlunoServiceImplBase() {
                @Override public void buscarAluno(BuscarAlunoRequest req, StreamObserver<AlunoResponse> resp) {
                    if (!req.getId().equals("aluno-da-ficha")) throw new AssertionError("Aluno diferente do dono da ficha");
                    if (status.get().equals("FALHA")) {
                        resp.onError(Status.UNAVAILABLE.asRuntimeException()); return;
                    }
                    resp.onNext(AlunoResponse.newBuilder().setId(req.getId()).setStatus(status.get()).build()); resp.onCompleted();
                }
            })
            .addService(new TreinoServiceGrpc.TreinoServiceImplBase() {
                @Override public void buscarFichaTreino(BuscarFichaTreinoRequest req, StreamObserver<FichaTreinoResponse> resp) {
                    resp.onNext(ficha()); resp.onCompleted();
                }
                @Override public void registrarExecucao(academia.grpc.RegistrarExecucaoRequest req, StreamObserver<FichaTreinoResponse> resp) {
                    escritas.incrementAndGet(); resp.onNext(ficha()); resp.onCompleted();
                }
            }).build().start();
        ManagedChannel channel = ManagedChannelBuilder.forAddress("127.0.0.1", server.getPort()).usePlaintext().build();
        try {
            var jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("test")
                .header("alg","HS256").subject("aluno@teste.com").claim("aluno_id","aluno-da-ficha").build();
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt,
                    java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ALUNO"))));
            var regra = new MatriculaTreinoService(AlunoServiceGrpc.newBlockingStub(channel));
            var controller = new TreinoController(TreinoServiceGrpc.newBlockingStub(channel), null, regra);
            var request = new RegistrarExecucaoRequest(List.of(new RegistrarExecucaoRequest.ExecucaoItemDto("item", 10.0, true)));
            conferir(controller.buscarFichaTreino("ficha").getStatusCode().value() == 200, "historico de inativo continua consultavel");
            esperarErro(controller, request, Status.Code.FAILED_PRECONDITION);
            conferir(escritas.get() == 0, "inativo rejeitado antes de gravar execucao");
            status.set("ATIVO");
            conferir(controller.registrarExecucaoTreino("ficha", request).getStatusCode().value() == 200 && escritas.get() == 1, "ativo pode registrar execucao");
            status.set("INATIVO");
            esperarErro(controller, request, Status.Code.FAILED_PRECONDITION);
            conferir(escritas.get() == 1, "inativacao apos abrir a ficha tambem bloqueia nova gravacao");
            status.set("FALHA");
            esperarErro(controller, request, Status.Code.UNAVAILABLE);
            conferir(escritas.get() == 1, "falha ao conferir matricula impede gravacao");
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }
    private static FichaTreinoResponse ficha() {
        return FichaTreinoResponse.newBuilder().setId("ficha").setAlunoId("aluno-da-ficha").setStatus("PENDENTE").build();
    }
    private static void esperarErro(TreinoController controller, RegistrarExecucaoRequest request, Status.Code codigo) {
        try { controller.registrarExecucaoTreino("ficha", request); throw new AssertionError("Gravacao deveria ser rejeitada"); }
        catch (StatusRuntimeException ex) { if (ex.getStatus().getCode() != codigo) throw ex; }
    }
    private static void conferir(boolean valor, String mensagem) {
        if (!valor) throw new AssertionError(mensagem);
        System.out.println("[OK] " + mensagem);
    }
}
