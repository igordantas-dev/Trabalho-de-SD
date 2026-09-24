package academia.gateway.dto;

import academia.grpc.MensalidadeResponse;

/**
 * DTO de resposta para representação de Mensalidade no Gateway REST.
 */
public record MensalidadeResponseDto(
        String id,
        String alunoId,
        double valor,
        String dataVencimento,
        String dataPagamento,
        String status,
        String formaPagamento,
        String descricao,
        String mensagem
) {
    public static MensalidadeResponseDto fromProto(MensalidadeResponse proto) {
        return new MensalidadeResponseDto(
                proto.getId(),
                proto.getAlunoId(),
                proto.getValor(),
                proto.getDataVencimento(),
                proto.getDataPagamento(),
                proto.getStatus(),
                proto.getFormaPagamento(),
                proto.getDescricao(),
                proto.getMensagem()
        );
    }
}
