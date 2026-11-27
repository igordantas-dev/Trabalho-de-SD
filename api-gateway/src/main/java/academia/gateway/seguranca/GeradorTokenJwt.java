package academia.gateway.seguranca;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Utilitário autônomo para geração de tokens JWT assinado com algoritmo HMAC-SHA256 (HS256).
 * Segue exatamente o padrão didático apresentado.
 *
 * Pode ser executado via terminal ou invocado pelo serviço de autenticação do Gateway.
 */
public class GeradorTokenJwt {

    public static String gerarToken(String sujeito, String perfil, String alunoId, String segredo, long duracaoMinutos) {
        try {
            MACSigner signer = new MACSigner(segredo.getBytes(StandardCharsets.UTF_8));
            Date agora = new Date();
            Date expiracao = new Date(agora.getTime() + (duracaoMinutos * 60 * 1000));

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(sujeito)
                    .issueTime(agora)
                    .expirationTime(expiracao)
                    .claim("perfil", perfil)
                    .claim("aluno_id", alunoId)
                    .claim("auth_version", "2")
                    .build();

            SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            signedJWT.sign(signer);

            return signedJWT.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Falha ao gerar token JWT: " + e.getMessage(), e);
        }
    }

}
