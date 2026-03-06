package com.otp.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import java.nio.charset.StandardCharsets;

@Configuration
public class RateLimiterConfig {

    @Bean
    public KeyResolver phoneEmailKeyResolver() {
        return exchange ->
                DataBufferUtils.join(exchange.getRequest().getBody())
                        .map(dataBuffer -> {

                            byte[] bytes = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(bytes);
                            DataBufferUtils.release(dataBuffer);

                            String body = new String(bytes, StandardCharsets.UTF_8);

                            if (body.contains("\"phone\"")) {
                                String phone = body.replaceAll(".*\\\"phone\\\"\\s*:\\s*\\\"([^\\\"]+)\\\".*", "$1");
                                return "phone:" + phone;
                            }

                            if (body.contains("\"email\"")) {
                                String email = body.replaceAll(".*\\\"email\\\"\\s*:\\s*\\\"([^\\\"]+)\\\".*", "$1");
                                return "email:" + email;
                            }

                            if (exchange.getRequest().getRemoteAddress() != null
                                    && exchange.getRequest().getRemoteAddress().getAddress() != null) {
                                String ip = exchange.getRequest()
                                        .getRemoteAddress()
                                        .getAddress()
                                        .getHostAddress();
                                return "ip:" + ip;
                            }

                            return "ip:unknown";
                        })
                        .switchIfEmpty(Mono.fromSupplier(() -> {
                            if (exchange.getRequest().getRemoteAddress() != null
                                    && exchange.getRequest().getRemoteAddress().getAddress() != null) {
                                String ip = exchange.getRequest()
                                        .getRemoteAddress()
                                        .getAddress()
                                        .getHostAddress();
                                return "ip:" + ip;
                            }
                            return "ip:unknown";
                        }));
    }
}
