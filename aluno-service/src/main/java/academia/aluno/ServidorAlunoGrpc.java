package academia.aluno;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Ponto de entrada do Microsserviço de Alunos.
 *
 * Inicializa o EntityManagerFactory do JPA apontando para o PostgreSQL
 * via variáveis de ambiente (mesmo padrão da Aula 6):
 *   DB_URL      → jdbc:postgresql://HOST:5432/academia?sslmode=require
 *   DB_USER     → postgres
 *   DB_PASSWORD → senha_do_banco
 *
 * Em seguida sobe o servidor gRPC na porta 9091.
 */
public class ServidorAlunoGrpc {

    public static final int PORTA = 9091;

    public static void main(String[] args) throws IOException, InterruptedException {
        // ── Configuração do JPA a partir de variáveis de ambiente ──────────
        Map<String, String> jpaProps = new HashMap<>();
        jpaProps.put("jakarta.persistence.jdbc.url",
            System.getenv().getOrDefault("DB_URL",
                "jdbc:postgresql://localhost:5432/academia"));
        jpaProps.put("jakarta.persistence.jdbc.user",
            System.getenv().getOrDefault("DB_USER", "postgres"));
        jpaProps.put("jakarta.persistence.jdbc.password",
            System.getenv().getOrDefault("DB_PASSWORD", ""));
        // ddl-auto=update: o Hibernate cria/atualiza a tabela automaticamente
        jpaProps.put("hibernate.hbm2ddl.auto", "update");
        jpaProps.put("hibernate.dialect",
            "org.hibernate.dialect.PostgreSQLDialect");
        // Exibe as SQLs no console para facilitar debug na apresentação
        jpaProps.put("hibernate.show_sql", "true");
        jpaProps.put("hibernate.format_sql", "true");

        EntityManagerFactory emf = Persistence.createEntityManagerFactory(
            "aluno-pu", jpaProps);
        EntityManager em = emf.createEntityManager();

        // ── Inicialização do servidor gRPC ─────────────────────────────────
        Server server = ServerBuilder.forPort(PORTA)
                .addService(new AlunoServiceImpl(em))
                .build();

        server.start();
        System.out.println("Servidor de Alunos gRPC rodando na porta " + PORTA);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[AlunoService] Encerrando servidor...");
            server.shutdown();
            em.close();
            emf.close();
        }));

        server.awaitTermination();
    }
}
