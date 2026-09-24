package academia.gateway.excecao;

import io.grpc.StatusRuntimeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handler global de exceções do API Gateway.
 *
 * Segue exatamente o padrão da Aula 6 (ValidacaoExceptionHandler):
 *   - MethodArgumentNotValidException → 400 com mapa de erros por campo
 *   - StatusRuntimeException (gRPC)   → traduzido para HTTP semântico
 *   - Exception (genérico)            → 500
 */
@RestControllerAdvice
public class GatewayExceptionHandler {

    /**
     * Trata falhas de Bean Validation (@NotBlank, @NotNull, @Positive).
     * Retorna 400 Bad Request com o mapa campo → mensagem de erro.
     * Mesmo formato da Aula 6.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidacao(
            MethodArgumentNotValidException ex) {

        Map<String, String> erros = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        f -> f.getField(),
                        f -> f.getDefaultMessage(),
                        (a, b) -> a  // em caso de campos duplicados, mantém o primeiro
                ));

        return ResponseEntity.badRequest().body(Map.of(
                "mensagem", "Dados inválidos",
                "erros", erros
        ));
    }

    /**
     * Traduz erros gRPC (StatusRuntimeException) para HTTP semântico.
     *
     * Mapeamento:
     *   NOT_FOUND         → 404 Not Found
     *   ALREADY_EXISTS    → 409 Conflict
     *   INVALID_ARGUMENT  → 400 Bad Request
     *   FAILED_PRECONDITION → 422 Unprocessable Entity
     *   UNAVAILABLE       → 503 Service Unavailable (microsserviço fora do ar)
     *   outros            → 500 Internal Server Error
     */
    @ExceptionHandler(StatusRuntimeException.class)
    public ResponseEntity<Map<String, String>> handleGrpc(StatusRuntimeException ex) {
        String descricao = ex.getStatus().getDescription() != null
                ? ex.getStatus().getDescription()
                : "Erro no microsserviço interno";

        HttpStatus httpStatus = switch (ex.getStatus().getCode()) {
            case NOT_FOUND          -> HttpStatus.NOT_FOUND;
            case ALREADY_EXISTS     -> HttpStatus.CONFLICT;
            case INVALID_ARGUMENT   -> HttpStatus.BAD_REQUEST;
            case FAILED_PRECONDITION -> HttpStatus.UNPROCESSABLE_ENTITY;
            case UNAVAILABLE        -> HttpStatus.SERVICE_UNAVAILABLE;
            default                 -> HttpStatus.INTERNAL_SERVER_ERROR;
        };

        return ResponseEntity.status(httpStatus).body(Map.of(
                "mensagem", descricao,
                "codigo", ex.getStatus().getCode().name()
        ));
    }

    /** Fallback para qualquer exceção não mapeada. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenerico(Exception ex) {
        return ResponseEntity.internalServerError().body(Map.of(
                "mensagem", "Erro interno no Gateway",
                "detalhe", ex.getMessage() != null ? ex.getMessage() : "sem detalhe"
        ));
    }
}
