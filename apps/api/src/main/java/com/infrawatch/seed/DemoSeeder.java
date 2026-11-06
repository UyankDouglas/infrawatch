package com.infrawatch.seed;

import com.infrawatch.auth.User;
import com.infrawatch.auth.UserRepository;
import com.infrawatch.metrics.AgentService;
import com.infrawatch.organization.MemberRole;
import com.infrawatch.organization.Organization;
import com.infrawatch.organization.OrganizationMember;
import com.infrawatch.organization.OrganizationMemberRepository;
import com.infrawatch.organization.OrganizationRepository;
import com.infrawatch.resource.Environment;
import com.infrawatch.resource.MonitoredResource;
import com.infrawatch.resource.MonitoredResourceRepository;
import com.infrawatch.resource.ResourceGroup;
import com.infrawatch.resource.ResourceGroupRepository;
import com.infrawatch.resource.ResourceType;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seed de demonstração (ativada por infrawatch.seed.enabled=true).
 * Cria a organização "Demo Corp", três usuários (um por perfil) e os três serviços de demonstração.
 * Idempotente: não recria o que já existe.
 */
@Component
@ConditionalOnProperty(prefix = "infrawatch.seed", name = "enabled", havingValue = "true")
public class DemoSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);
    public static final String DEMO_PASSWORD = "InfraWatch@2026";

    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final OrganizationMemberRepository members;
    private final MonitoredResourceRepository resources;
    private final ResourceGroupRepository groups;
    private final PasswordEncoder encoder;
    private final SeedProperties properties;
    private final AgentService agentService;

    public DemoSeeder(UserRepository users, OrganizationRepository organizations, OrganizationMemberRepository members,
                      MonitoredResourceRepository resources, ResourceGroupRepository groups, PasswordEncoder encoder,
                      SeedProperties properties, AgentService agentService) {
        this.agentService = agentService;
        this.users = users;
        this.organizations = organizations;
        this.members = members;
        this.resources = resources;
        this.groups = groups;
        this.encoder = encoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.existsByEmail("admin@demo.infrawatch.local")) {
            log.info("Seed de demonstração já aplicada; nada a fazer.");
            return;
        }
        String hash = encoder.encode(DEMO_PASSWORD);
        User admin = users.save(new User("admin@demo.infrawatch.local", hash, "Ana Administradora"));
        User operator = users.save(new User("operador@demo.infrawatch.local", hash, "Otávio Operador"));
        User viewer = users.save(new User("visualizador@demo.infrawatch.local", hash, "Vera Visualizadora"));

        Organization org = organizations.save(new Organization("Demo Corp", "demo-corp"));
        members.save(new OrganizationMember(org, admin, MemberRole.ADMIN));
        members.save(new OrganizationMember(org, operator, MemberRole.OPERATOR));
        members.save(new OrganizationMember(org, viewer, MemberRole.VIEWER));

        // Segunda organização para demonstrar isolamento.
        User other = users.save(new User("admin@outra.infrawatch.local", hash, "Olga Outra"));
        Organization otherOrg = organizations.save(new Organization("Outra Empresa", "outra-empresa"));
        members.save(new OrganizationMember(otherOrg, other, MemberRole.ADMIN));

        ResourceGroup apis = groups.save(new ResourceGroup(org.getId(), "APIs de demonstração",
                "Serviços Docker locais usados para demonstrar o InfraWatch"));

        String base = properties.demoBaseUrl();
        createResource(org, apis, admin, "API Saudável", "Serviço de demonstração que responde rápido e com sucesso.",
                base + ":9101/health", Environment.PRODUCTION, 60, 3000, 800, Set.of("demo", "saudavel"));
        createResource(org, apis, admin, "API Degradada", "Serviço de demonstração com latência controladamente elevada.",
                base + ":9102/health", Environment.STAGING, 60, 5000, 800, Set.of("demo", "lenta"));
        createResource(org, apis, admin, "API Instável", "Serviço de demonstração com falhas controladas via painel.",
                base + ":9103/health", Environment.DEVELOPMENT, 60, 3000, 800, Set.of("demo", "instavel"));

        if (properties.demoAgentToken() != null && !properties.demoAgentToken().isBlank()) {
            MonitoredResource server = new MonitoredResource(org.getId(), "Servidor de demonstração",
                    ResourceType.AGENT_SERVER, "demo-agent", Environment.PRODUCTION, admin.getId());
            server.setDescription("Container Linux com o agente InfraWatch reportando CPU, memória, disco e uptime.");
            server.setGroup(apis);
            server.setOwner("Equipe de Infraestrutura");
            server.setTags(Set.of("demo", "agente"));
            resources.save(server);
            agentService.registerWithKnownToken(org.getId(), server.getId(), "agente-demo", 15,
                    properties.demoAgentToken(), admin.getId());
            log.info("Agente de demonstração registrado para o recurso \"Servidor de demonstração\".");
        }

        log.info("Seed de demonstração aplicada. Usuários: admin@demo.infrawatch.local / operador@... / visualizador@... (senha: {})",
                DEMO_PASSWORD);
    }

    private void createResource(Organization org, ResourceGroup group, User creator, String name, String description,
                                String url, Environment env, int interval, int timeout, int degradedMs, Set<String> tags) {
        MonitoredResource r = new MonitoredResource(org.getId(), name, ResourceType.HEALTH_ENDPOINT, url, env, creator.getId());
        r.setDescription(description);
        r.setGroup(group);
        r.setOwner("Equipe de Plataforma");
        r.setTags(tags);
        r.getCheckConfig().setIntervalSeconds(interval);
        r.getCheckConfig().setTimeoutMs(timeout);
        r.getCheckConfig().setDegradedLatencyMs(degradedMs);
        r.getCheckConfig().setFailureThreshold(3);
        r.getCheckConfig().setRecoveryThreshold(2);
        resources.save(r);
    }
}
