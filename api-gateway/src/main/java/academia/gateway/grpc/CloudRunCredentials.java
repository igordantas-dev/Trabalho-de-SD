package academia.gateway.grpc;

import io.grpc.CallCredentials;
import io.grpc.Metadata;
import io.grpc.Status;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executor;

/** ID token da conta de servico. Utilizado apenas no Cloud Run, sem chaves JSON. */
final class CloudRunCredentials extends CallCredentials {
    private final String audience;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private String token;
    private long expiraEm;
    CloudRunCredentials(String audience) { this.audience = audience; }
    @Override public void applyRequestMetadata(RequestInfo request, Executor executor, MetadataApplier applier) {
        executor.execute(() -> {
            try {
                Metadata headers = new Metadata();
                headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer " + token());
                applier.apply(headers);
            } catch (Exception error) {
                applier.fail(Status.UNAUTHENTICATED.withDescription("Falha na identidade do servico"));
            }
        });
    }
    private synchronized String token() throws Exception {
        long agora = System.currentTimeMillis();
        if (token != null && agora < expiraEm - 60000) return token;
        URI uri = URI.create("http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/identity?audience="
            + URLEncoder.encode(audience, StandardCharsets.UTF_8));
        var resposta = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(3))
            .header("Metadata-Flavor", "Google").GET().build(), HttpResponse.BodyHandlers.ofString());
        if (resposta.statusCode() != 200) throw new IllegalStateException("Identity unavailable");
        String recebido = resposta.body();
        expiraEm = SignedJWT.parse(recebido).getJWTClaimsSet().getExpirationTime().getTime();
        token = recebido;
        return token;
    }
}
