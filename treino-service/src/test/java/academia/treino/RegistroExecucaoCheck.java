package academia.treino;

import academia.grpc.*;
import academia.treino.entidade.FichaTreino;
import academia.treino.entidade.ItemTreino;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import java.lang.reflect.Proxy;
import java.util.UUID;

/** Verifica o handler real com entidades em memoria; nao substitui o teste de persistencia. */
public class RegistroExecucaoCheck {
    private static boolean ativa;
    private static FichaTreinoResponse resposta;
    private static Throwable erro;

    public static void main(String[] args) throws Exception {
        var ficha = new FichaTreino("aluno-teste", "Teste", "HIPERTROFIA");
        atribuirId(ficha);
        var primeiro = new ItemTreino("Supino", "Peito", 3, 10, 20.0);
        var segundo = new ItemTreino("Remada", "Costas", 3, 10, 15.0);
        atribuirId(primeiro); atribuirId(segundo);
        ficha.addItem(primeiro); ficha.addItem(segundo);
        var tx = (EntityTransaction) Proxy.newProxyInstance(EntityTransaction.class.getClassLoader(),
            new Class<?>[]{EntityTransaction.class}, (proxy, method, values) -> switch (method.getName()) {
                case "begin" -> { ativa = true; yield null; }
                case "commit", "rollback" -> { ativa = false; yield null; }
                case "isActive" -> ativa;
                default -> throw new UnsupportedOperationException(method.getName());
            });
        var em = (EntityManager) Proxy.newProxyInstance(EntityManager.class.getClassLoader(),
            new Class<?>[]{EntityManager.class}, (proxy, method, values) -> switch (method.getName()) {
                case "getTransaction" -> tx;
                case "find" -> ficha.getId().equals(values[1]) ? ficha : null;
                case "merge" -> values[0];
                default -> throw new UnsupportedOperationException(method.getName());
            });
        var service = new TreinoServiceImpl(em);
        executar(service, ficha, item(primeiro, false), item(segundo, false));
        conferir(erro == null && resposta.getStatus().equals("PENDENTE") && resposta.getDataConclusao().isEmpty(), "nenhum exercicio feito");
        executar(service, ficha, item(primeiro, true));
        conferir(erro == null && resposta.getStatus().equals("PENDENTE") && resposta.getDataConclusao().isEmpty(), "progresso parcial preserva pendencia");
        executar(service, ficha, item(segundo, true));
        conferir(erro == null && resposta.getStatus().equals("CONCLUIDO") && !resposta.getDataConclusao().isEmpty(), "todos feitos concluem a ficha");
        String data = resposta.getDataConclusao();
        executar(service, ficha, item(segundo, true));
        conferir(erro == null && resposta.getDataConclusao().equals(data), "salvar novamente preserva data de conclusao");
        executar(service, ficha, item(primeiro, false));
        conferir(erro == null && resposta.getStatus().equals("PENDENTE") && resposta.getDataConclusao().isEmpty(), "desmarcar reabre a ficha e limpa a data");
        executar(service, ficha, item(primeiro, true), ExecucaoItemRequest.newBuilder().setItemId(UUID.randomUUID().toString()).setConcluido(true).build());
        conferir(erro != null && Status.fromThrowable(erro).getCode() == Status.Code.INVALID_ARGUMENT && !primeiro.getConcluido(), "item de outra ficha rejeitado sem alterar itens validos");
        executar(service, ficha, item(primeiro, true).toBuilder().setCargaUsada(-1).build());
        conferir(erro != null && Status.fromThrowable(erro).getCode() == Status.Code.INVALID_ARGUMENT && !ativa, "carga negativa rejeitada");
        ficha.getItens().clear();
        executar(service, ficha);
        conferir(erro == null && resposta.getStatus().equals("PENDENTE"), "ficha vazia nao e concluida");
    }

    private static void executar(TreinoServiceImpl service, FichaTreino ficha, ExecucaoItemRequest... itens) {
        resposta = null; erro = null;
        service.registrarExecucao(RegistrarExecucaoRequest.newBuilder().setFichaId(ficha.getId().toString()).addAllItens(java.util.List.of(itens)).build(), new StreamObserver<>() {
            public void onNext(FichaTreinoResponse value) { resposta = value; }
            public void onError(Throwable value) { erro = value; }
            public void onCompleted() { }
        });
    }
    private static ExecucaoItemRequest item(ItemTreino item, boolean feito) {
        return ExecucaoItemRequest.newBuilder().setItemId(item.getId().toString()).setCargaUsada(10).setConcluido(feito).build();
    }
    private static void atribuirId(Object entidade) throws Exception {
        var field = entidade.getClass().getDeclaredField("id"); field.setAccessible(true); field.set(entidade, UUID.randomUUID());
    }
    private static void conferir(boolean condicao, String cenario) {
        if (!condicao) throw new AssertionError(cenario);
        System.out.println("[OK] " + cenario);
    }
}
