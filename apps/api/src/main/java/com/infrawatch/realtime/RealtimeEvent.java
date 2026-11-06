package com.infrawatch.realtime;

import java.time.Instant;
import java.util.UUID;

/**
 * Envelope de todo evento enviado por WebSocket. {@code seq} é monotônico por organização enquanto a
 * instância estiver no ar; o cliente usa saltos de sequência para disparar uma ressincronização via REST.
 */
public record RealtimeEvent(String id, long seq, String type, UUID organizationId, Instant occurredAt, Object payload) {}
