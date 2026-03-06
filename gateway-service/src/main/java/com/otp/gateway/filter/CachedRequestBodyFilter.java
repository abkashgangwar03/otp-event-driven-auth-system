package com.otp.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
public class CachedRequestBodyFilter implements GlobalFilter, Ordered {

    public static final String CACHED_REQUEST_BODY_ATTR = "cachedRequestBody";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        if (exchange.getRequest().getHeaders().getContentLength() == 0) {
            return chain.filter(exchange);
        }

        return DataBufferUtils.join(exchange.getRequest().getBody())
                .flatMap(dataBuffer -> {

                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);

                    String body = new String(bytes, StandardCharsets.UTF_8);

                    exchange.getAttributes().put(CACHED_REQUEST_BODY_ATTR, body);

                    Flux<DataBuffer> cachedFlux = Flux.defer(() -> {
                        DataBuffer buffer = exchange.getResponse()
                                .bufferFactory()
                                .wrap(bytes);
                        return Mono.just(buffer);
                    });

                    ServerHttpRequest mutatedRequest = exchange.getRequest()
                            .mutate()
                            .header("X-Cached-Body", "true")
                            .build();

                    ServerWebExchange mutatedExchange = exchange.mutate()
                            .request(new org.springframework.http.server.reactive.ServerHttpRequestDecorator(mutatedRequest) {
                                @Override
                                public Flux<DataBuffer> getBody() {
                                    return cachedFlux;
                                }
                            })
                            .build();

                    return chain.filter(mutatedExchange);
                });
    }

    @Override
    public int getOrder() {
        return -2;
    }
}
