package academia.gateway.servico;

import academia.grpc.AlunoServiceGrpc;
import academia.grpc.BuscarAlunoRequest;
import io.grpc.Status;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;

@Service
public class MatriculaTreinoService {
    private final AlunoServiceGrpc.AlunoServiceBlockingStub alunoStub;

    @Value("${grpc.deadline-seconds:5}")
    private long deadlineSegundos = 5;

    public MatriculaTreinoService(AlunoServiceGrpc.AlunoServiceBlockingStub alunoStub) {
        this.alunoStub = alunoStub;
    }

    public void validarRegistroExecucao(String alunoId) {
        var aluno = alunoStub.withDeadlineAfter(deadlineSegundos, TimeUnit.SECONDS)
            .buscarAluno(BuscarAlunoRequest.newBuilder().setId(alunoId).build());
        if ("INATIVO".equalsIgnoreCase(aluno.getStatus())) {
            throw Status.FAILED_PRECONDITION.withDescription(
                "Matrícula inativa. O histórico pode ser consultado, mas o registro de execução de treinos está bloqueado. Procure a administração da academia.")
                .asRuntimeException();
        }
    }
}
