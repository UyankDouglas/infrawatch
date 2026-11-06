package com.infrawatch.auth;

import com.infrawatch.audit.AuditService;
import com.infrawatch.common.ApiException;
import com.infrawatch.integrations.IntegrationsProperties;
import com.infrawatch.metrics.AgentTokens;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recuperação de senha: token de uso único (1 h), guardado como hash, enviado por e-mail quando o SMTP está
 * configurado e o envio externo habilitado; caso contrário o link é registrado no log do servidor (desenvolvimento).
 * A resposta é sempre a mesma, exista ou não a conta.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final IntegrationsProperties integrations;
    private final AuditService audit;

    public PasswordResetService(UserRepository users, PasswordResetTokenRepository tokens, PasswordEncoder encoder,
                                ObjectProvider<JavaMailSender> mailSender, IntegrationsProperties integrations, AuditService audit) {
        this.users = users;
        this.tokens = tokens;
        this.encoder = encoder;
        this.mailSender = mailSender;
        this.integrations = integrations;
        this.audit = audit;
    }

    /** Retorna o link gerado apenas para uso interno/testes; a API nunca o expõe. */
    @Transactional
    public Optional<String> requestReset(String rawEmail) {
        String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase(Locale.ROOT);
        Optional<User> user = users.findByEmail(email);
        if (user.isEmpty()) return Optional.empty();
        String token = AgentTokens.generate().token().replace("iwa_", "pwd_");
        tokens.save(new PasswordResetToken(user.get().getId(), AgentTokens.hash(token), Instant.now().plus(Duration.ofHours(1))));
        String link = integrations.baseUrl() + "/redefinir-senha/" + token;
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender != null && integrations.dispatchEnabled()) {
            try {
                SimpleMailMessage msg = new SimpleMailMessage();
                msg.setFrom(integrations.fromAddress());
                msg.setTo(email);
                msg.setSubject("[InfraWatch] Redefinição de senha");
                msg.setText("Recebemos um pedido para redefinir sua senha.\n\nAbra o link (válido por 1 hora): " + link
                        + "\n\nSe não foi você, ignore esta mensagem.");
                sender.send(msg);
            } catch (RuntimeException e) {
                log.warn("Falha ao enviar e-mail de redefinição: {}", e.getMessage());
            }
        } else {
            log.info("[DEV] Link de redefinição de senha para {}: {}", email, link);
        }
        audit.record(null, user.get().getId(), "auth.password_reset_requested", "user", user.get().getId(), "Redefinição de senha solicitada");
        return Optional.of(link);
    }

    @Transactional
    public void reset(String token, String newPassword) {
        PasswordResetToken t = tokens.findByTokenHash(AgentTokens.hash(token == null ? "" : token))
                .filter(x -> x.isUsable(Instant.now()))
                .orElseThrow(() -> ApiException.badRequest("reset_token_invalid", "Link inválido ou expirado. Solicite uma nova redefinição."));
        User user = users.findById(t.getUserId()).orElseThrow(() -> ApiException.badRequest("reset_token_invalid", "Conta não encontrada."));
        user.setPasswordHash(encoder.encode(newPassword));
        t.use(Instant.now());
        audit.record(null, user.getId(), "auth.password_reset", "user", user.getId(), "Senha redefinida por link de recuperação");
    }
}
