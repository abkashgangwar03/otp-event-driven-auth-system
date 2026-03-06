package com.otp.gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import java.util.HashMap;
import java.util.Map;

@RestController
public class FallbackController {

    @RequestMapping("/fallback")
    public ResponseEntity<Map<String, Object>> fallback(ServerHttpRequest request) {

        Map<String, Object> response = new HashMap<>();

        response.put("error", "SERVICE_UNAVAILABLE");
        response.put("message", "Auth service temporarily unavailable");
        response.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        response.put("path", request.getURI().getPath());

        String correlationId = request.getHeaders().getFirst("X-Correlation-Id");
        if (correlationId != null) {
            response.put("correlationId", correlationId);
        }

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }
}
