package academia.treino;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class TreinoApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void consultaMatriculaPersistidaAoGerarTreino() throws Exception {
        mockMvc.perform(post("/treinos")
                        .contentType("application/json")
                        .content("{\"idAluno\":\"ALUNO-101\",\"objetivo\":\"HIPERTROFIA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusMatricula", is("ATIVA")))
                .andExpect(jsonPath("$.exercicios[0].nome", is("Supino Reto")));
    }

    @Test
    void bloqueiaCheckinDeMatriculaInadimplente() throws Exception {
        mockMvc.perform(post("/checkins")
                        .contentType("application/json")
                        .content("{\"idAluno\":\"ALUNO-102\",\"idUnidade\":\"UNIDADE-CENTRO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autorizado", is(false)))
                .andExpect(jsonPath("$.mensagem", is("Check-in bloqueado. Situação cadastral: INADIMPLENTE")));
    }
}