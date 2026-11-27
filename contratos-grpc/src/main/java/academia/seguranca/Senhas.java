package academia.seguranca;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Hash com salt individual. Nenhuma senha em texto puro e persistida. */
public final class Senhas {
    private static final int ITERACOES = 600_000;
    private Senhas() { }
    public static void validar(String senha) {
        if (senha == null || senha.length() < 8 || senha.length() > 128)
            throw new IllegalArgumentException("A senha deve ter entre 8 e 128 caracteres.");
    }
    public static String gerar(String senha) {
        validar(senha);
        byte[] salt = new byte[16]; new SecureRandom().nextBytes(salt);
        return "pbkdf2_sha256$" + ITERACOES + "$" + Base64.getEncoder().encodeToString(salt)
            + "$" + Base64.getEncoder().encodeToString(derivar(senha, salt, ITERACOES));
    }
    public static boolean verificar(String senha, String hash) {
        if (senha == null || senha.length() > 128 || hash == null || hash.isBlank()) return false;
        try {
            String[] partes = hash.split("\\$", -1);
            if (partes.length != 4 || !partes[0].equals("pbkdf2_sha256")) return false;
            int iteracoes = Integer.parseInt(partes[1]);
            if (iteracoes < ITERACOES || iteracoes > 1_000_000) return false;
            byte[] salt = Base64.getDecoder().decode(partes[2]);
            byte[] esperado = Base64.getDecoder().decode(partes[3]);
            if (salt.length < 16 || esperado.length != 32) return false;
            return MessageDigest.isEqual(esperado, derivar(senha, salt, iteracoes));
        } catch (IllegalArgumentException ex) { return false; }
    }
    private static byte[] derivar(String senha, byte[] salt, int iteracoes) {
        var spec = new PBEKeySpec(senha.toCharArray(), salt, iteracoes, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        catch (Exception ex) { throw new IllegalStateException("Falha ao proteger credencial", ex); }
        finally { spec.clearPassword(); }
    }
}
