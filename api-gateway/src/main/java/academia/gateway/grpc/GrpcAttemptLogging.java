package academia.gateway.grpc;

import io.grpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.atomic.AtomicLong;

/** Logs transport attempts without request bodies, tokens or database credentials. */
final class GrpcAttemptLogging implements ClientInterceptor {
    private static final Logger LOG = LoggerFactory.getLogger(GrpcAttemptLogging.class);
    private static final AtomicLong CALL_IDS = new AtomicLong();

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method, CallOptions options, Channel next) {
        long callId = CALL_IDS.incrementAndGet();
        return next.newCall(method, options.withStreamTracerFactory(new ClientStreamTracer.Factory() {
            @Override
            public ClientStreamTracer newClientStreamTracer(
                    ClientStreamTracer.StreamInfo info, Metadata headers) {
                int attempt = info.getPreviousAttempts() + 1;
                LOG.info("grpc_call={} method={} attempt={} transparent_retry={}",
                        callId, method.getFullMethodName(), attempt, info.isTransparentRetry());
                return new ClientStreamTracer() {
                    @Override
                    public void streamClosed(Status status) {
                        if (!status.isOk()) {
                            LOG.warn("grpc_call={} method={} attempt={} status={}",
                                    callId, method.getFullMethodName(), attempt, status.getCode());
                        }
                    }
                };
            }
        }));
    }
}
