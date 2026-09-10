package academia.treino;

import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;

public class ServidorTreinoGrpc {
    public static final int PORTA = 9091;

    public static void main(String[] args) throws IOException, InterruptedException {
        Server server = ServerBuilder.forPort(PORTA)
                .addService(new TreinoServiceImpl())
                .build();

        server.start();
        System.out.println("Servidor de Treinos gRPC rodando na porta " + PORTA);
        server.awaitTermination();
    }
}