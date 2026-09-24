package academia.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ponto de entrada do API Gateway da Academia.
 *
 * Responsabilidades:
 *   - Receber requisições HTTP/JSON do Frontend ou Postman
 *   - Validar os dados (Bean Validation — mesmo padrão da Aula 6)
 *   - Traduzir REST → gRPC e despachar para os microsserviços internos
 *   - Retornar respostas semânticas: 200, 201, 400, 404, 409
 *
 * Porta: 8080 (padrão Spring Boot)
 *
 * Microsserviços internos (gRPC):
 *   aluno-service     → localhost:9091
 *   treino-service    → localhost:9092
 *   financeiro-service→ localhost:9093
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
