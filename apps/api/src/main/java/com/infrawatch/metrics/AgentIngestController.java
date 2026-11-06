package com.infrawatch.metrics;

import com.infrawatch.metrics.IngestService.AuthenticatedAgent;
import com.infrawatch.metrics.MetricsDtos.IngestResponse;
import com.infrawatch.metrics.MetricsDtos.SamplePayload;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint de ingestão usado pelos agentes. Autenticação por token Bearer (sem sessão/CSRF).
 * É somente de entrada: o servidor nunca devolve instruções ao agente além do intervalo sugerido.
 */
@RestController
@RequestMapping("/api/v1/agents")
public class AgentIngestController {

    private final IngestService ingestService;

    public AgentIngestController(IngestService ingestService) {
        this.ingestService = ingestService;
    }

    @PostMapping("/ingest")
    public ResponseEntity<IngestResponse> ingest(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                                 @Valid @RequestBody SamplePayload payload) {
        AuthenticatedAgent auth = ingestService.authenticate(authorization);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ingestService.ingest(auth, payload));
    }
}
