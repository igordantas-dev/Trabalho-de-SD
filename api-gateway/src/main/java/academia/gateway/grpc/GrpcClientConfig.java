package academia.gateway.grpc;

import academia.grpc.AlunoServiceGrpc;
import academia.grpc.FinanceiroServiceGrpc;
import academia.grpc.TreinoServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração dos canais gRPC e stubs bloqueantes para cada microsserviço.
 *
 * Cada stub é um @Bean Spring injetado diretamente nos Controllers,
 * eliminando a necessidade de criar canais manualmente em cada classe.
 *
 * Os hosts e portas são lidos do application.properties e podem ser
 * sobrescritos por variáveis de ambiente na VM — igual ao padrão DB_URL da Aula 6.
 */
@Configuration
public class GrpcClientConfig {

    // ─── Aluno Service ────────────────────────────────────────────────────

    @Bean
    public AlunoServiceGrpc.AlunoServiceBlockingStub alunoServiceStub(
            @Value("${grpc.aluno.host}") String host,
            @Value("${grpc.aluno.port}") int port) {

        ManagedChannel canal = ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext()  // sem TLS na comunicação interna
                .build();

        System.out.println("[GatewayConfig] Canal Aluno gRPC conectado em " + host + ":" + port);
        return AlunoServiceGrpc.newBlockingStub(canal);
    }

    // ─── Treino Service ───────────────────────────────────────────────────

    @Bean
    public TreinoServiceGrpc.TreinoServiceBlockingStub treinoServiceStub(
            @Value("${grpc.treino.host}") String host,
            @Value("${grpc.treino.port}") int port) {

        ManagedChannel canal = ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .build();

        System.out.println("[GatewayConfig] Canal Treino gRPC conectado em " + host + ":" + port);
        return TreinoServiceGrpc.newBlockingStub(canal);
    }

    // ─── Financeiro Service ───────────────────────────────────────────────

    @Bean
    public FinanceiroServiceGrpc.FinanceiroServiceBlockingStub financeiroServiceStub(
            @Value("${grpc.financeiro.host}") String host,
            @Value("${grpc.financeiro.port}") int port) {

        ManagedChannel canal = ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .build();

        System.out.println("[GatewayConfig] Canal Financeiro gRPC conectado em " + host + ":" + port);
        return FinanceiroServiceGrpc.newBlockingStub(canal);
    }
}
