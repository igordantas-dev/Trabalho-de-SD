package academia.aluno;
import academia.aluno.entidade.Aluno;
import academia.grpc.*;
import academia.seguranca.Senhas;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import jakarta.persistence.*;
import java.lang.reflect.Proxy;
import java.util.*;

/** Testa o handler real e o hash, com repositorio em memoria, sem tocar no banco. */
public class AlunoAuthCheck {
    static AlunoResponse resposta; static Throwable erro;
    public static void main(String[] args) throws Exception {
        Aluno aluno = new Aluno("Teste","12345678900","aluno@teste.com","",70.0,1.7);
        var id=Aluno.class.getDeclaredField("id");id.setAccessible(true);id.set(aluno,UUID.randomUUID());
        aluno.setSenhaHash(Senhas.gerar("SenhaInicial#2026"));
        EntityTransaction tx=(EntityTransaction) Proxy.newProxyInstance(EntityTransaction.class.getClassLoader(),new Class<?>[]{EntityTransaction.class},(p,m,a)->m.getName().equals("isActive")?false:null);
        EntityManager em=(EntityManager) Proxy.newProxyInstance(EntityManager.class.getClassLoader(),new Class<?>[]{EntityManager.class},(p,m,a)->switch(m.getName()) {
            case "getTransaction" -> tx;
            case "find" -> aluno.getId().equals(a[1])?aluno:null;
            case "createQuery" -> {
                final String[] email={""};
                yield Proxy.newProxyInstance(TypedQuery.class.getClassLoader(),new Class<?>[]{TypedQuery.class},(query,method,values)->switch(method.getName()) {
                    case "setParameter" -> {email[0]=values[1].toString();yield query;}
                    case "getResultList" -> aluno.getEmail().equalsIgnoreCase(email[0])?List.of(aluno):List.of();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
            }
            default -> throw new UnsupportedOperationException(m.getName());
        });
        var service=new AlunoServiceImpl(em);
        login(service,"aluno@teste.com","SenhaInicial#2026");check(erro==null && resposta.getId().equals(aluno.getId().toString()),"senha correta autentica aluno vinculado");
        login(service,"aluno@teste.com","errada");check(Status.fromThrowable(erro).getCode()==Status.Code.UNAUTHENTICATED,"senha incorreta rejeitada");
        login(service,"outro@teste.com","SenhaInicial#2026");check(Status.fromThrowable(erro).getCode()==Status.Code.UNAUTHENTICATED,"email inexistente rejeitado sem revelar cadastro");
        service.definirSenhaAluno(DefinirSenhaAlunoRequest.newBuilder().setAlunoId(aluno.getId().toString()).setSenha("SenhaNova#2026").build(),observer());
        login(service,"aluno@teste.com","SenhaInicial#2026");check(erro!=null,"senha anterior nao autentica apos redefinicao");
        login(service,"aluno@teste.com","SenhaNova#2026");check(erro==null,"nova senha autentica");
        aluno.setSenhaHash(null);login(service,"aluno@teste.com","SenhaNova#2026");check(erro!=null,"cadastro legado sem senha nao recebe acesso padrao");
        service.cadastrarAluno(CadastrarAlunoRequest.newBuilder().setNome("Teste").setCpf("123").setEmail("t@teste.com").setSenha("SenhaNova#2026").setAltura(170).build(),observer());
        check(Status.fromThrowable(erro).getCode()==Status.Code.INVALID_ARGUMENT,"microsservico tambem rejeita altura em centimetros");
    }
    static void login(AlunoServiceImpl service,String email,String senha) {
        service.autenticarAluno(AutenticarAlunoRequest.newBuilder().setEmail(email).setSenha(senha).build(),observer());
    }
    static StreamObserver<AlunoResponse> observer() {
        erro=null;resposta=null;return new StreamObserver<>() {
            public void onNext(AlunoResponse value){resposta=value;}public void onError(Throwable value){erro=value;}public void onCompleted(){}
        };
    }
    static void check(boolean value,String name){if(!value)throw new AssertionError(name);System.out.println("[OK] "+name);}
}
