package academia.treino;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class TreinoApplication {

    public static void main(String[] args) {
        SpringApplication.run(TreinoApplication.class, args);
    }

    @Bean
    CommandLineRunner iniciarGrpc(Server server) {
        return args -> {
            server.start();
            System.out.println("Servidor de Treinos gRPC rodando na porta " + ServidorTreinoGrpc.PORTA);
        };
    }

    @Bean(destroyMethod = "shutdown")
    Server servidorGrpc(TreinoServiceImpl service) {
        return ServerBuilder.forPort(ServidorTreinoGrpc.PORTA)
                .addService(service)
                .build();
    }
}