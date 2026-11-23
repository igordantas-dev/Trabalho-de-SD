package academia.financeiro;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Ponto de entrada do Microsserviço Financeiro.
 * Porta gRPC: 9093
 *
 * Variáveis de ambiente (mesmo padrão da Aula 6):
 *   DB_URL      → jdbc:postgresql://HOST:5432/academia?sslmode=require
 *   DB_USER     → postgres
 *   DB_PASSWORD → senha_do_banco
 */
public class ServidorFinanceiroGrpc {

    public static final int PORTA = Integer.parseInt(
            System.getenv().getOrDefault("PORT", "9093"));

    public static void main(String[] args) throws IOException, InterruptedException {
        Map<String, String> jpaProps = new HashMap<>();
        jpaProps.put("jakarta.persistence.jdbc.url",
            System.getenv().getOrDefault("DB_URL",
                "jdbc:postgresql://localhost:5432/academia"));
        String cloudSqlInstance = System.getenv("INSTANCE_CONNECTION_NAME");
        if (cloudSqlInstance != null && !cloudSqlInstance.isBlank()) {
            String database = System.getenv().getOrDefault("DB_NAME", "academia");
            if (!database.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                throw new IllegalArgumentException("Invalid DB_NAME");
            }
            if (System.getenv().getOrDefault("DB_PASSWORD", "").isBlank()) {
                throw new IllegalStateException("DB_PASSWORD is required for Cloud SQL");
            }
            String instance = java.net.URLEncoder.encode(
                    cloudSqlInstance, java.nio.charset.StandardCharsets.UTF_8);
            jpaProps.put("jakarta.persistence.jdbc.url",
                    "jdbc:postgresql:///" + database
                    + "?socketFactory=com.google.cloud.sql.postgres.SocketFactory"
                    + "&cloudSqlInstance=" + instance
                    + "&cloudSqlRefreshStrategy=lazy&ipTypes=PUBLIC");
        }
        jpaProps.put("hibernate.hikari.minimumIdle", "0");
        jpaProps.put("hibernate.hikari.maximumPoolSize",
                System.getenv().getOrDefault("DB_POOL_SIZE", "3"));
        jpaProps.put("jakarta.persistence.jdbc.user",
            System.getenv().getOrDefault("DB_USER", "postgres"));
        jpaProps.put("jakarta.persistence.jdbc.password",
            System.getenv().getOrDefault("DB_PASSWORD", ""));
        jpaProps.put("hibernate.hbm2ddl.auto",
                System.getenv().getOrDefault("DB_SCHEMA_MODE", "update"));
        jpaProps.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        jpaProps.put("hibernate.show_sql", "true");
        jpaProps.put("hibernate.format_sql", "true");

        EntityManagerFactory emf = Persistence.createEntityManagerFactory(
            "financeiro-pu", jpaProps);
        Server server = ServerBuilder.forPort(PORTA)
                .addService(new FinanceiroServiceImpl(emf))
                .build();

        server.start();
        System.out.println("Servidor Financeiro gRPC rodando na porta " + PORTA);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[FinanceiroService] Encerrando servidor...");
            server.shutdown();
            emf.close();
        }));

        server.awaitTermination();
    }
}
