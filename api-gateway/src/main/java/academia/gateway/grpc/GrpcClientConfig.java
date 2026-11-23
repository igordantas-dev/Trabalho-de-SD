package academia.gateway.grpc;

import academia.grpc.AlunoServiceGrpc;
import academia.grpc.FinanceiroServiceGrpc;
import academia.grpc.TreinoServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Local: plaintext on the Compose network.
 * Cloud Run: TLS to the public hostname on port 443; Google terminates TLS.
 * Channels are closed when Spring shuts down.
 */
@Configuration
public class GrpcClientConfig {
    private ManagedChannel criarCanal(String host, int port, boolean tls) {
        var builder = ManagedChannelBuilder.forAddress(host, port);
        if (tls) {
            builder.useTransportSecurity();
        } else {
            builder.usePlaintext();
        }
        return builder.build();
    }

    @Bean(destroyMethod = "shutdown")
    public ManagedChannel alunoChannel(
            @Value("${grpc.aluno.host}") String host,
            @Value("${grpc.aluno.port}") int port,
            @Value("${grpc.tls:false}") boolean tls) {
        return criarCanal(host, port, tls);
    }

    @Bean(destroyMethod = "shutdown")
    public ManagedChannel treinoChannel(
            @Value("${grpc.treino.host}") String host,
            @Value("${grpc.treino.port}") int port,
            @Value("${grpc.tls:false}") boolean tls) {
        return criarCanal(host, port, tls);
    }

    @Bean(destroyMethod = "shutdown")
    public ManagedChannel financeiroChannel(
            @Value("${grpc.financeiro.host}") String host,
            @Value("${grpc.financeiro.port}") int port,
            @Value("${grpc.tls:false}") boolean tls) {
        return criarCanal(host, port, tls);
    }

    @Bean
    public AlunoServiceGrpc.AlunoServiceBlockingStub alunoServiceStub(
            @Qualifier("alunoChannel") ManagedChannel channel) {
        return AlunoServiceGrpc.newBlockingStub(channel);
    }

    @Bean
    public TreinoServiceGrpc.TreinoServiceBlockingStub treinoServiceStub(
            @Qualifier("treinoChannel") ManagedChannel channel) {
        return TreinoServiceGrpc.newBlockingStub(channel);
    }

    @Bean
    public FinanceiroServiceGrpc.FinanceiroServiceBlockingStub financeiroServiceStub(
            @Qualifier("financeiroChannel") ManagedChannel channel) {
        return FinanceiroServiceGrpc.newBlockingStub(channel);
    }
}
