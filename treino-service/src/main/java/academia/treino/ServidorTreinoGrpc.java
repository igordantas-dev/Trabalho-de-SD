package academia.treino;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Ponto de entrada do Microsserviço de Treinos.
 * Porta gRPC: 9092
 *
 * Variáveis de ambiente (mesmo padrão da Aula 6):
 *   DB_URL      → jdbc:postgresql://HOST:5432/academia?sslmode=require
 *   DB_USER     → postgres
 *   DB_PASSWORD → senha_do_banco
 */
public class ServidorTreinoGrpc {

    public static final int PORTA = 9092;

    public static void main(String[] args) throws IOException, InterruptedException {
        // Configuração JPA via variáveis de ambiente
        Map<String, String> jpaProps = new HashMap<>();
        jpaProps.put("jakarta.persistence.jdbc.url",
            System.getenv().getOrDefault("DB_URL",
                "jdbc:postgresql://localhost:5432/academia"));
        jpaProps.put("jakarta.persistence.jdbc.user",
            System.getenv().getOrDefault("DB_USER", "postgres"));
        jpaProps.put("jakarta.persistence.jdbc.password",
            System.getenv().getOrDefault("DB_PASSWORD", ""));
        jpaProps.put("hibernate.hbm2ddl.auto", "update");
        jpaProps.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        jpaProps.put("hibernate.show_sql", "true");
        jpaProps.put("hibernate.format_sql", "true");

        EntityManagerFactory emf = Persistence.createEntityManagerFactory(
            "treino-pu", jpaProps);
        Server server = ServerBuilder.forPort(PORTA)
                .addService(new TreinoServiceImpl(emf))
                .build();

        server.start();
        System.out.println("Servidor de Treinos gRPC rodando na porta " + PORTA);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[TreinoService] Encerrando servidor...");
            server.shutdown();
            emf.close();
        }));

        server.awaitTermination();
    }
}