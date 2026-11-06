package com.infrawatch.realtime;

import com.infrawatch.organization.OrganizationMemberRepository;
import com.infrawatch.security.AppUserDetails;
import com.infrawatch.security.SecurityProperties;
import java.security.Principal;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP sobre WebSocket em {@code /ws}.
 *
 * Segurança:
 *  - O handshake HTTP passa pela cadeia do Spring Security (rota autenticada) e o Principal da sessão
 *    é propagado para a sessão STOMP; CONNECT sem usuário é recusado.
 *  - SUBSCRIBE a {@code /topic/org/{id}} só é aceito se o usuário for membro da organização — isolamento
 *    entre organizações garantido no servidor, não no cliente.
 *  - Clientes não publicam: não há prefixo de aplicação; mensagens SEND são descartadas.
 *  - Origens permitidas = origens CORS configuradas.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);
    private static final Pattern ORG_TOPIC = Pattern.compile("^/topic/org/([0-9a-fA-F-]{36})(/.*)?$");

    private final SecurityProperties securityProperties;
    private final OrganizationMemberRepository members;
    private final org.springframework.scheduling.TaskScheduler taskScheduler;

    public WebSocketConfig(SecurityProperties securityProperties, OrganizationMemberRepository members,
                           @org.springframework.beans.factory.annotation.Qualifier("messageBrokerTaskScheduler")
                           @org.springframework.context.annotation.Lazy
                           org.springframework.scheduling.TaskScheduler taskScheduler) {
        this.securityProperties = securityProperties;
        this.members = members;
        this.taskScheduler = taskScheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(securityProperties.corsAllowedOrigins().toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{10_000, 10_000})
                .setTaskScheduler(taskScheduler);
        // O scheduler dedicado do broker (messageBrokerTaskScheduler) evita ambiguidade com o scheduler da aplicação.
        registry.setUserDestinationPrefix("/user");
        // Sem setApplicationDestinationPrefixes: o servidor nunca recebe comandos dos clientes.
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null || accessor.getCommand() == null) return message;
                Principal user = accessor.getUser();
                switch (accessor.getCommand()) {
                    case CONNECT -> {
                        if (user == null) {
                            log.warn("WebSocket CONNECT recusado: sessão não autenticada");
                            throw new IllegalArgumentException("Autenticação necessária.");
                        }
                    }
                    case SUBSCRIBE -> authorizeSubscription(user, accessor.getDestination());
                    case SEND -> throw new IllegalArgumentException("Clientes não publicam mensagens.");
                    default -> { }
                }
                return message;
            }
        });
    }

    private void authorizeSubscription(Principal user, String destination) {
        if (user == null || destination == null) {
            throw new IllegalArgumentException("Assinatura não autorizada.");
        }
        AppUserDetails details = principalDetails(user);
        if (destination.startsWith("/user/")) {
            return; // fila privada do próprio usuário (resolvida pelo prefixo /user)
        }
        Matcher m = ORG_TOPIC.matcher(destination);
        if (!m.matches() || details == null) {
            throw new IllegalArgumentException("Destino não permitido: " + destination);
        }
        UUID orgId = UUID.fromString(m.group(1));
        if (members.findByUserIdAndOrganizationId(details.getId(), orgId).isEmpty()) {
            log.warn("Assinatura recusada: usuário {} não pertence à organização {}", details.getId(), orgId);
            throw new IllegalArgumentException("Você não é membro desta organização.");
        }
    }

    private static AppUserDetails principalDetails(Principal principal) {
        if (principal instanceof Authentication auth && auth.getPrincipal() instanceof AppUserDetails d) return d;
        if (principal instanceof UsernamePasswordAuthenticationToken t && t.getPrincipal() instanceof AppUserDetails d) return d;
        return null;
    }
}
