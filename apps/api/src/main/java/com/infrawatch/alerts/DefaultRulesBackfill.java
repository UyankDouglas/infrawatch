package com.infrawatch.alerts;

import com.infrawatch.organization.Organization;
import com.infrawatch.organization.OrganizationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Na inicialização, garante regras padrão para organizações criadas antes da Fase 3 (idempotente:
 * só age em organizações sem nenhuma regra).
 */
@Component
public class DefaultRulesBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultRulesBackfill.class);

    private final OrganizationRepository organizations;
    private final AlertService alerts;

    public DefaultRulesBackfill(OrganizationRepository organizations, AlertService alerts) {
        this.organizations = organizations;
        this.alerts = alerts;
    }

    @Override
    public void run(ApplicationArguments args) {
        int created = 0;
        for (Organization org : organizations.findAll()) {
            created += alerts.ensureDefaultRules(org.getId(), null);
        }
        if (created > 0) {
            log.info("Regras de alerta padrão criadas para organizações existentes: {}", created);
        }
    }
}
