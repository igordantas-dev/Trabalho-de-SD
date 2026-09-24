package academia.treino;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MatriculaDataInitializer {

    @Bean
    CommandLineRunner carregarMatriculasIniciais(MatriculaRepository repository) {
        return args -> {
            inserirSeAusente(repository, "ALUNO-101", StatusMatricula.ATIVA);
            inserirSeAusente(repository, "ALUNO-102", StatusMatricula.INADIMPLENTE);
            inserirSeAusente(repository, "ALUNO-103", StatusMatricula.ATIVA);
        };
    }

    private void inserirSeAusente(MatriculaRepository repository, String idAluno, StatusMatricula status) {
        if (!repository.existsById(idAluno)) {
            repository.save(new Matricula(idAluno, status));
        }
    }
}