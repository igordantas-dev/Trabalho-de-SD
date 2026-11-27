package academia.gateway.excecao;

import io.grpc.StatusRuntimeException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handler global de exceções do API Gateway.
 * Garante respostas semânticas, claras e detalhadas em português para todos os erros.
 */
@RestControllerAdvice
public class GatewayExceptionHandler {

    /**
     * Trata falhas de Bean Validation (@NotBlank, @NotNull, @Positive, etc.).
     * Retorna 400 Bad Request com mapa detalhado de erros por campo.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidacao(
            MethodArgumentNotValidException ex) {

        Map<String, String> erros = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        f -> f.getField(),
                        f -> f.getDefaultMessage() != null ? f.getDefaultMessage() : "campo inválido",
                        (a, b) -> a
                ));

        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("status", HttpStatus.BAD_REQUEST.value());
        corpo.put("codigo", "DADOS_INVALIDOS");
        corpo.put("mensagem", "Dados de entrada inválidos. Verifique os campos informados.");
        corpo.put("erros", erros);
        corpo.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.badRequest().body(corpo);
    }

    /**
     * Trata erros de JSON inválido ou incompatibilidade de tipo no payload da requisição.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleJsonInvalido(HttpMessageNotReadableException ex) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("status", HttpStatus.BAD_REQUEST.value());
        corpo.put("codigo", "REQUISICAO_MALFORMADA");
        corpo.put("mensagem", "Corpo da requisição JSON inválido ou mal formatado. Verifique tipos de dados e campos obrigatórios.");
        corpo.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.badRequest().body(corpo);
    }

    /**
     * Traduz erros de microsserviços gRPC (StatusRuntimeException) para HTTP semântico.
     */
    @ExceptionHandler(StatusRuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleGrpc(StatusRuntimeException ex) {
        String descricaoOriginal = ex.getStatus().getDescription();

        HttpStatus httpStatus;
        String mensagemAmigavel;

        switch (ex.getStatus().getCode()) {
            case NOT_FOUND -> {
                httpStatus = HttpStatus.NOT_FOUND;
                mensagemAmigavel = (descricaoOriginal != null && !descricaoOriginal.isBlank())
                        ? descricaoOriginal : "Recurso solicitado não foi encontrado no sistema.";
            }
            case ALREADY_EXISTS -> {
                httpStatus = HttpStatus.CONFLICT;
                mensagemAmigavel = (descricaoOriginal != null && !descricaoOriginal.isBlank())
                        ? descricaoOriginal : "O registro informado já existe no banco de dados.";
            }
            case INVALID_ARGUMENT -> {
                httpStatus = HttpStatus.BAD_REQUEST;
                mensagemAmigavel = (descricaoOriginal != null && !descricaoOriginal.isBlank())
                        ? descricaoOriginal : "Parâmetros informados são inválidos.";
            }
            case FAILED_PRECONDITION -> {
                httpStatus = HttpStatus.UNPROCESSABLE_ENTITY;
                mensagemAmigavel = (descricaoOriginal != null && !descricaoOriginal.isBlank())
                        ? descricaoOriginal : "A operação não pode ser executada no estado atual do recurso.";
            }
            case UNAVAILABLE -> {
                httpStatus = HttpStatus.SERVICE_UNAVAILABLE;
                mensagemAmigavel = "Este recurso está temporariamente indisponível. Tente novamente em instantes.";
            }
            case DEADLINE_EXCEEDED -> {
                httpStatus = HttpStatus.GATEWAY_TIMEOUT;
                mensagemAmigavel = "A operação demorou mais que o esperado. Tente novamente em instantes.";
            }
            case PERMISSION_DENIED -> {
                httpStatus = HttpStatus.FORBIDDEN;
                mensagemAmigavel = "Acesso negado para a operação solicitada.";
            }
            case UNAUTHENTICATED -> {
                httpStatus = HttpStatus.UNAUTHORIZED;
                mensagemAmigavel = "E-mail ou senha incorretos, ou sessão expirada. Entre novamente.";
            }
            default -> {
                httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
                mensagemAmigavel = "Não foi possível concluir a operação. Tente novamente em instantes.";
            }
        }

        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("status", httpStatus.value());
        corpo.put("codigo", ex.getStatus().getCode().name());
        corpo.put("mensagem", mensagemAmigavel);
        corpo.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.status(httpStatus).body(corpo);
    }

    /**
     * Trata parâmetros inválidos (ex: UUID com formato incorreto).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("status", HttpStatus.BAD_REQUEST.value());
        corpo.put("codigo", "ARGUMENTO_INVALIDO");
        corpo.put("mensagem", ex.getMessage() != null ? ex.getMessage() : "Argumento inválido informado na requisição.");
        corpo.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.badRequest().body(corpo);
    }

    /**
     * Trata métodos HTTP não suportados na rota.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("status", HttpStatus.METHOD_NOT_ALLOWED.value());
        corpo.put("codigo", "METODO_NAO_PERMITIDO");
        corpo.put("mensagem", "Método HTTP " + ex.getMethod() + " não suportado nesta rota.");
        corpo.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(corpo);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAcesso(AccessDeniedException ex) {
        return ResponseEntity.status(403).body(Map.of("status",403,"codigo","ACESSO_NEGADO",
            "mensagem","Você não tem permissão para acessar estes dados ou realizar esta operação."));
    }

    /** Fallback para qualquer exceção não mapeada. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenerico(Exception ex) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        corpo.put("codigo", "ERRO_INTERNO_GATEWAY");
        corpo.put("mensagem", "Não foi possível concluir a operação. Tente novamente em instantes.");
        corpo.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.internalServerError().body(corpo);
    }
}
