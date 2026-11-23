package academia.gateway.grpc;

import academia.grpc.AlunoServiceGrpc;
import academia.grpc.FinanceiroServiceGrpc;
import academia.grpc.TreinoServiceGrpc;
import java.util.List;
import java.util.Map;

/** Retry only read RPCs. A call's existing deadline covers all attempts and backoff. */
final class GrpcRetryPolicy {
    private GrpcRetryPolicy() {}

    static Map<String, ?> config() {
        var names = List.of(
                name(AlunoServiceGrpc.SERVICE_NAME, "BuscarAluno"),
                name(AlunoServiceGrpc.SERVICE_NAME, "ListarAlunos"),
                name(TreinoServiceGrpc.SERVICE_NAME, "BuscarFichaTreino"),
                name(TreinoServiceGrpc.SERVICE_NAME, "ListarFichasPorAluno"),
                name(FinanceiroServiceGrpc.SERVICE_NAME, "BuscarMensalidade"),
                name(FinanceiroServiceGrpc.SERVICE_NAME, "ListarMensalidadesPorAluno"));
        return Map.of("methodConfig", List.of(Map.of(
                "name", names,
                "retryPolicy", Map.of(
                        "maxAttempts", 3.0,
                        "initialBackoff", "0.2s",
                        "maxBackoff", "1s",
                        "backoffMultiplier", 2.0,
                        "retryableStatusCodes", List.of("UNAVAILABLE")))));
    }

    private static Map<String, String> name(String service, String method) {
        return Map.of("service", service, "method", method);
    }
}
