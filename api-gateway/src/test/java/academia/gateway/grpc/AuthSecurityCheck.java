package academia.gateway.grpc;

import academia.gateway.ApiGatewayApplication;
import academia.seguranca.Senhas;
import academia.grpc.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import java.net.URI;
import java.net.http.*;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Testa HTTP real, Spring Security e gRPC; nao altera o banco da aplicacao. */
public class AuthSecurityCheck {
    static final ObjectMapper JSON = new ObjectMapper();
    static final HttpClient HTTP = HttpClient.newHttpClient();
    static String base;
    public static void main(String[] args) throws Exception {
        String senha = "SenhaTeste#2026";
        String hash = Senhas.gerar(senha);
        conferir(Senhas.verificar(senha,hash) && !Senhas.verificar("errada",hash), "hash valida senha correta e rejeita incorreta");
        conferir(!hash.equals(Senhas.gerar(senha)) && !hash.contains(senha), "hashes com salt individual sem senha em texto puro");
        var escritas = new AtomicInteger();
        Server grpc = ServerBuilder.forPort(0)
            .addService(new AlunoServiceGrpc.AlunoServiceImplBase() {
                @Override public void autenticarAluno(AutenticarAlunoRequest req, StreamObserver<AlunoResponse> resp) {
                    if (!req.getEmail().equals("aluno@teste.com") || !req.getSenha().equals("SenhaAluno#2026")) {
                        resp.onError(Status.UNAUTHENTICATED.asRuntimeException()); return;
                    }
                    resp.onNext(aluno()); resp.onCompleted();
                }
                @Override public void buscarAluno(BuscarAlunoRequest req, StreamObserver<AlunoResponse> resp) {
                    resp.onNext(aluno()); resp.onCompleted();
                }
                @Override public void listarAlunos(ListarAlunosRequest req, StreamObserver<ListaAlunosResponse> resp) {
                    resp.onNext(ListaAlunosResponse.newBuilder().addAlunos(aluno()).build()); resp.onCompleted();
                }
            })
            .addService(new TreinoServiceGrpc.TreinoServiceImplBase() {
                @Override public void buscarFichaTreino(BuscarFichaTreinoRequest req, StreamObserver<FichaTreinoResponse> resp) {
                    resp.onNext(FichaTreinoResponse.newBuilder().setId(req.getId()).setAlunoId(req.getId().equals("outra") ? "aluno-B" : "aluno-A").build());resp.onCompleted();
                }
                @Override public void registrarExecucao(RegistrarExecucaoRequest req, StreamObserver<FichaTreinoResponse> resp) {
                    escritas.incrementAndGet();resp.onNext(FichaTreinoResponse.newBuilder().setId(req.getFichaId()).setAlunoId("aluno-A").build());resp.onCompleted();
                }
            })
            .addService(new FinanceiroServiceGrpc.FinanceiroServiceImplBase() {
                @Override public void buscarMensalidade(BuscarMensalidadeRequest req, StreamObserver<MensalidadeResponse> resp) {
                    if (req.getId().equals("falha")) {
                        resp.onError(Status.INTERNAL.withDescription("org.postgresql: detalhe tecnico privado").asRuntimeException()); return;
                    }
                    resp.onNext(MensalidadeResponse.newBuilder().setAlunoId("aluno-B").build());resp.onCompleted();
                }
            }).build().start();
        try (var app = new SpringApplicationBuilder(ApiGatewayApplication.class).run(
            "--server.port=0", "--spring.main.banner-mode=off", "--logging.level.root=ERROR",
            "--jwt.secret="+"chave-apenas-de-teste-com-mais-de-32-caracteres",
            "--ADMIN_LOGIN=gestao@teste.com", "--ADMIN_PASSWORD_HASH="+hash,
            "--grpc.aluno.host=127.0.0.1", "--grpc.treino.host=127.0.0.1", "--grpc.financeiro.host=127.0.0.1",
            "--grpc.aluno.port="+grpc.getPort(), "--grpc.treino.port="+grpc.getPort(), "--grpc.financeiro.port="+grpc.getPort(),"--GRPC_IAM=false")) {
            base = "http://127.0.0.1:"+((ServletWebServerApplicationContext)app).getWebServer().getPort();
            conferir(request("GET","/api/listarAlunos",null,null).statusCode()==401,"API exige login");
            conferir(request("GET","/api/gerarToken",null,null).statusCode()!=200,"emissao publica de token removida");
            conferir(login("ADMIN","gestao@teste.com","errada").statusCode()==401,"gestao rejeita senha incorreta");
            conferir(login("ALUNO","aluno@teste.com","errada").statusCode()==401,"aluno rejeita senha incorreta");
            String admin = token(login("ADMIN","gestao@teste.com",senha));
            String aluno = token(login("ALUNO","aluno@teste.com","SenhaAluno#2026"));
            conferir(request("GET","/api/listarAlunos",null,admin).statusCode()==200,"gestao autenticada pode listar alunos");
            conferir(request("GET","/api/listarAlunos",null,aluno).statusCode()==403,"aluno nao pode listar outras pessoas");
            conferir(request("GET","/api/buscarAluno/aluno-B",null,aluno).statusCode()==403,"aluno nao pode consultar outro cadastro");
            conferir(request("GET","/api/buscarFichaTreino/outra",null,aluno).statusCode()==403,"aluno nao pode consultar ficha de outra pessoa");
            conferir(request("GET","/api/buscarMensalidade/outra",null,aluno).statusCode()==403,"aluno nao pode consultar mensalidade de outra pessoa");
            var falhaInterna = request("GET","/api/buscarMensalidade/falha",null,admin);
            conferir(falhaInterna.statusCode()==500 && !falhaInterna.body().contains("postgresql") && !falhaInterna.body().contains("detalhe tecnico"),"erro interno tratado sem expor detalhes do microsservico");
            String body = "{\"itens\":[{\"itemId\":\"item\",\"cargaUsada\":10,\"concluido\":true}]}";
            conferir(request("POST","/api/registrarExecucaoTreino/propria",body,admin).statusCode()==403,"gestao nao altera execucao do aluno");
            conferir(request("POST","/api/registrarExecucaoTreino/outra",body,aluno).statusCode()==403 && escritas.get()==0,"escrita em ficha alheia bloqueada antes do microsservico");
            conferir(request("POST","/api/registrarExecucaoTreino/propria",body,aluno).statusCode()==200 && escritas.get()==1,"aluno registra somente a propria execucao");
            String cadastro = JSON.writeValueAsString(Map.of("nome","Teste","cpf","12345678900","email","a@teste.com","altura",170,"peso",70,"senha","SenhaAluno#2026"));
            var altura = request("POST","/api/cadastrarAluno",cadastro,admin);
            conferir(altura.statusCode()==400 && altura.body().contains("metros"),"altura 170m rejeitada com orientacao sobre metros");
            var invalido = request("POST","/api/cadastrarAluno","{",admin);
            conferir(invalido.statusCode()==400 && !invalido.body().contains("com.fasterxml"),"JSON invalido retorna mensagem sem detalhes internos");
        } finally { grpc.shutdownNow().awaitTermination(5,TimeUnit.SECONDS); }
    }
    static AlunoResponse aluno() { return AlunoResponse.newBuilder().setId("aluno-A").setEmail("aluno@teste.com").setNome("Aluno").setStatus("ATIVO").build(); }
    static HttpResponse<String> login(String perfil,String email,String senha) throws Exception {
        return request("POST","/api/autenticar",JSON.writeValueAsString(Map.of("perfil",perfil,"username",email,"senha",senha)),null);
    }
    static String token(HttpResponse<String> response) throws Exception {
        conferir(response.statusCode()==200,"login valido retorna token");return JSON.readTree(response.body()).get("token").asText();
    }
    static HttpResponse<String> request(String method,String path,String body,String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(base+path)).header("Content-Type","application/json");
        if(token!=null) builder.header("Authorization","Bearer "+token);
        builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body));
        return HTTP.send(builder.build(),HttpResponse.BodyHandlers.ofString());
    }
    static void conferir(boolean valor,String mensagem) { if(!valor) throw new AssertionError(mensagem);System.out.println("[OK] "+mensagem); }
}
