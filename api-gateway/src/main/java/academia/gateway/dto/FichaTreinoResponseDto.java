package academia.gateway.dto;

import academia.grpc.FichaTreinoResponse;
import academia.grpc.ItemTreinoResponse;

import java.util.List;

/**
 * DTO de resposta para representação de Ficha de Treino e seus Itens no Gateway REST.
 */
public record FichaTreinoResponseDto(
        String id,
        String alunoId,
        String titulo,
        String objetivo,
        String status,
        String dataCriacao,
        String dataConclusao,
        List<ItemTreinoResponseDto> itens,
        String mensagem
) {
    public record ItemTreinoResponseDto(
            String id,
            String nomeExercicio,
            String grupoMuscular,
            int series,
            int repeticoes,
            double cargaSugerida,
            double cargaUsada,
            boolean concluido
    ) {
        public static ItemTreinoResponseDto fromProto(ItemTreinoResponse proto) {
            return new ItemTreinoResponseDto(
                    proto.getId(),
                    proto.getNomeExercicio(),
                    proto.getGrupoMuscular(),
                    proto.getSeries(),
                    proto.getRepeticoes(),
                    proto.getCargaSugerida(),
                    proto.getCargaUsada(),
                    proto.getConcluido()
            );
        }
    }

    public static FichaTreinoResponseDto fromProto(FichaTreinoResponse proto) {
        return new FichaTreinoResponseDto(
                proto.getId(),
                proto.getAlunoId(),
                proto.getTitulo(),
                proto.getObjetivo(),
                proto.getStatus(),
                proto.getDataCriacao(),
                proto.getDataConclusao(),
                proto.getItensList().stream().map(ItemTreinoResponseDto::fromProto).toList(),
                proto.getMensagem()
        );
    }
}
