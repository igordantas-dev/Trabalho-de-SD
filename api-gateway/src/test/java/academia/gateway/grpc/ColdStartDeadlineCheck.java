package academia.gateway.grpc;

import academia.gateway.controller.AlunoController;
import academia.grpc.*;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import java.util.Map;
import java.util.concurrent.*;

/** Checks Spring configuration and a real controller against a slow gRPC server. */
public final class ColdStartDeadlineCheck {
    public static void main(String[] args) throws Exception {
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
        Server server = ServerBuilder.forPort(0).addService(new AlunoServiceGrpc.AlunoServiceImplBase() {
            @Override
            public void listarAlunos(ListarAlunosRequest request, StreamObserver<ListaAlunosResponse> response) {
                timer.schedule(() -> {
                    response.onNext(ListaAlunosResponse.getDefaultInstance());
                    response.onCompleted();
                }, 6, TimeUnit.SECONDS);
            }
        }).build().start();
        try {
            try (var ctx = context(server.getPort(), null)) {
                try {
                    ctx.getBean(AlunoController.class).listarAlunos();
                    throw new AssertionError("The default five-second deadline should expire");
                } catch (StatusRuntimeException ex) {
                    if (ex.getStatus().getCode() != Status.Code.DEADLINE_EXCEEDED) throw ex;
                    System.out.println("[OK] Default local deadline remains five seconds");
                }
            }
            try (var ctx = context(server.getPort(), "20")) {
                var result = ctx.getBean(AlunoController.class).listarAlunos();
                if (result.getStatusCode().value() != 200 || result.getBody() == null) {
                    throw new AssertionError("Expected a successful read with the cloud deadline");
                }
                System.out.println("[OK] GRPC_DEADLINE_SECONDS=20 allows a six-second gRPC response");
            }
            System.out.println("ALL COLD START DEADLINE CHECKS PASSED");
        } finally {
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            timer.shutdownNow();
        }
    }

    private static AnnotationConfigApplicationContext context(int port, String deadline) throws Exception {
        var ctx = new AnnotationConfigApplicationContext();
        ctx.getEnvironment().getPropertySources().addLast(
                new ResourcePropertySource("classpath:application.properties"));
        if (deadline != null) {
            ctx.getEnvironment().getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                    "cloud-test-environment", Map.of("GRPC_DEADLINE_SECONDS", deadline)));
        }
        ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test-services", Map.of(
                "grpc.aluno.host", "127.0.0.1", "grpc.aluno.port", port,
                "grpc.treino.host", "127.0.0.1", "grpc.treino.port", port,
                "grpc.financeiro.host", "127.0.0.1", "grpc.financeiro.port", port,
                "grpc.tls", false)));
        ctx.register(GrpcClientConfig.class, AlunoController.class);
        ctx.refresh();
        return ctx;
    }
}
