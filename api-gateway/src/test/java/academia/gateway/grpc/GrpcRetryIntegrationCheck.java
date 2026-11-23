package academia.gateway.grpc;

import academia.grpc.*;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Executable integration check using a real gRPC server on an ephemeral port.
 * Run with exec:java and test classpath; no database or GCP credentials required.
 */
public final class GrpcRetryIntegrationCheck {
    private static final ConcurrentMap<String, AtomicInteger> COUNTS = new ConcurrentHashMap<>();

    public static void main(String[] args) throws Exception {
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
        Server server = ServerBuilder.forPort(0).addService(new AlunoServiceGrpc.AlunoServiceImplBase() {
            @Override
            public void buscarAluno(BuscarAlunoRequest request, StreamObserver<AlunoResponse> observer) {
                int attempt = count(request.getId());
                switch (request.getId()) {
                    case "recover" -> {
                        if (attempt < 3) {
                            observer.onError(Status.UNAVAILABLE.asRuntimeException());
                        } else {
                            observer.onNext(AlunoResponse.newBuilder().setId("recover").build());
                            observer.onCompleted();
                        }
                    }
                    case "missing" -> observer.onError(Status.NOT_FOUND.asRuntimeException());
                    case "slow" -> timer.schedule(() -> {
                        observer.onNext(AlunoResponse.getDefaultInstance());
                        observer.onCompleted();
                    }, 2, TimeUnit.SECONDS);
                    default -> observer.onError(Status.UNAVAILABLE.asRuntimeException());
                }
            }

            @Override
            public void cadastrarAluno(CadastrarAlunoRequest request, StreamObserver<AlunoResponse> observer) {
                count("write");
                observer.onError(Status.UNAVAILABLE.asRuntimeException());
            }
        }).build().start();
        ManagedChannel channel = null;
        try {
            channel = new GrpcClientConfig().alunoChannel("127.0.0.1", server.getPort(), false);
            var stub = AlunoServiceGrpc.newBlockingStub(channel);
            var recovered = stub.withDeadlineAfter(5, TimeUnit.SECONDS).buscarAluno(request("recover"));
            check(recovered.getId().equals("recover") && attempts("recover") == 3,
                    "Read recovers on the third attempt");

            expect(Status.Code.UNAVAILABLE, () ->
                    stub.withDeadlineAfter(5, TimeUnit.SECONDS).buscarAluno(request("down")));
            check(attempts("down") == 3, "Unavailable read stops after three attempts");

            expect(Status.Code.NOT_FOUND, () ->
                    stub.withDeadlineAfter(5, TimeUnit.SECONDS).buscarAluno(request("missing")));
            check(attempts("missing") == 1, "Not-found is not retried");

            expect(Status.Code.UNAVAILABLE, () ->
                    stub.withDeadlineAfter(5, TimeUnit.SECONDS).cadastrarAluno(
                            CadastrarAlunoRequest.getDefaultInstance()));
            check(attempts("write") == 1, "Writes have no application retry policy");

            long start = System.nanoTime();
            expect(Status.Code.DEADLINE_EXCEEDED, () ->
                    stub.withDeadlineAfter(150, TimeUnit.MILLISECONDS).buscarAluno(request("slow")));
            check(attempts("slow") == 1
                            && TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 1500,
                    "Deadline bounds total wait and is not retried");
            System.out.println("ALL GRPC RESILIENCE CHECKS PASSED");
        } finally {
            if (channel != null) channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            timer.shutdownNow();
        }
    }

    private static BuscarAlunoRequest request(String id) {
        return BuscarAlunoRequest.newBuilder().setId(id).build();
    }
    private static int count(String key) {
        return COUNTS.computeIfAbsent(key, ignored -> new AtomicInteger()).incrementAndGet();
    }
    private static int attempts(String key) {
        return COUNTS.get(key).get();
    }
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
        System.out.println("[OK] " + message);
    }
    private static void expect(Status.Code code, Runnable call) {
        try {
            call.run();
        } catch (StatusRuntimeException ex) {
            if (ex.getStatus().getCode() != code) throw new AssertionError(ex);
            return;
        }
        throw new AssertionError("Expected " + code);
    }
}
