package academia.gateway.dto;

import academia.grpc.AlunoResponse;

/**
 * DTO de resposta para representação de Aluno no Gateway REST.
 */
public record AlunoResponseDto(
        String id,
        String nome,
        String cpf,
        String email,
        String telefone,
        Double peso,
        Double altura,
        String status,
        String mensagem
) {
    public static AlunoResponseDto fromProto(AlunoResponse proto) {
        return new AlunoResponseDto(
                proto.getId(),
                proto.getNome(),
                proto.getCpf(),
                proto.getEmail(),
                proto.getTelefone(),
                proto.getPeso(),
                proto.getAltura(),
                proto.getStatus(),
                proto.getMensagem()
        );
    }
}
