package academia.atendimento;

import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;

public class ServidorAtendimentoGrpc {
    public static final int PORTA = 9090;

    public static void main(String[] args) throws IOException, InterruptedException {
        Server server = ServerBuilder.forPort(PORTA)
                .addService(new AtendimentoServiceImpl())
                .build();

        server.start();
        System.out.println("Servidor de Atendimento gRPC rodando na porta " + PORTA);
        server.awaitTermination();
    }
}