package com.infrawatch.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infrawatch.monitoring.CheckOutcome;
import com.infrawatch.monitoring.CheckResultApplier;
import com.infrawatch.support.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.UUID;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class ReportsIT extends AbstractIntegrationTest {

    @Autowired CheckResultApplier applier;

    private void outcome(UUID rid, boolean ok, int ms) {
        Instant now = Instant.now();
        applier.apply(rid, UUID.randomUUID(), ok
                ? new CheckOutcome(true, 200, ms, null, null, "127.0.0.1", now, now.plusMillis(ms))
                : new CheckOutcome(false, 503, ms, "unexpected_status", "503", "127.0.0.1", now, now.plusMillis(ms)));
    }

    @Test
    @DisplayName("relatório de disponibilidade e de incidentes refletem dados persistidos, com fórmula e modo de manutenção explícitos")
    void availabilityAndIncidentReports() throws Exception {
        Session s = register("Admin", "Org Relatórios");
        UUID rid = UUID.fromString(createResource(s, "API Relatório", "http://localhost:9101/health",
                "{\"intervalSeconds\":30,\"failureThreshold\":1,\"recoveryThreshold\":1}"));
        outcome(rid, true, 20);
        outcome(rid, false, 5);   // DOWN → incidente automático
        outcome(rid, true, 40);   // UP → resolve

        String av = getJson(s, "/api/v1/reports/availability?period=24h&maintenance=excluded");
        assertThat(JsonPath.<String>read(av, "$.maintenanceMode")).isEqualTo("excluded");
        assertThat(JsonPath.<String>read(av, "$.formula")).contains("tempo disponível").contains("EXCLUÍDOS");
        assertThat(JsonPath.<Integer>read(av, "$.rows.length()")).isEqualTo(1);
        assertThat(JsonPath.<String>read(av, "$.rows[0].name")).isEqualTo("API Relatório");
        assertThat(JsonPath.<Integer>read(av, "$.rows[0].checks")).isEqualTo(3);
        assertThat(JsonPath.<Integer>read(av, "$.rows[0].failedChecks")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(av, "$.rows[0].incidents")).isEqualTo(1);
        assertThat(JsonPath.<Double>read(av, "$.rows[0].mttrSeconds")).isNotNull();
        assertThat(JsonPath.<String>read(av, "$.rows[0].mtbfNote")).contains("2 incidentes");
        assertThat(JsonPath.<Double>read(av, "$.rows[0].avgResponseMs")).isEqualTo(30.0);
        assertThat(JsonPath.<Integer>read(av, "$.rows[0].maxResponseMs")).isEqualTo(40);

        String counted = getJson(s, "/api/v1/reports/availability?period=24h&maintenance=counted");
        assertThat(JsonPath.<String>read(counted, "$.formula")).contains("CONTADOS");

        String inc = getJson(s, "/api/v1/reports/incidents?period=24h");
        assertThat(JsonPath.<Integer>read(inc, "$.total")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(inc, "$.automatic")).isEqualTo(1);
        assertThat(JsonPath.<String>read(inc, "$.rows[0].resourceName")).isEqualTo("API Relatório");
        assertThat(JsonPath.<String>read(inc, "$.note")).contains("MTTR");

        String res = getJson(s, "/api/v1/reports/resources/" + rid + "?period=24h");
        assertThat(JsonPath.<String>read(res, "$.summary.resourceId")).isEqualTo(rid.toString());
        assertThat(JsonPath.<Integer>read(res, "$.incidents.length()")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(res, "$.latency.length()")).isGreaterThanOrEqualTo(1);

        // outra organização não vê nada
        Session other = register("Outro", "Org Outra Rel");
        assertThat(JsonPath.<Integer>read(getJson(other, "/api/v1/reports/availability"), "$.rows.length()")).isZero();
        mvc.perform(get("/api/v1/reports/resources/" + rid).cookie(other.cookie())).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("exportações CSV e XLSX ficam no histórico, podem ser baixadas e respeitam o isolamento por organização")
    void exportsCsvAndXlsx() throws Exception {
        Session s = register("Admin", "Org Export");
        UUID rid = UUID.fromString(createResource(s, "Exportável", "http://localhost:9101/health"));
        outcome(rid, true, 15);

        String csv = mvc.perform(post("/api/v1/reports/exports").with(csrf()).cookie(s.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"availability\",\"format\":\"csv\",\"period\":\"24h\",\"maintenance\":\"excluded\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.format").value("CSV"))
                .andExpect(jsonPath("$.rowCount").value(1))
                .andExpect(jsonPath("$.typeLabel").value("Disponibilidade"))
                .andReturn().getResponse().getContentAsString();
        String csvId = JsonPath.read(csv, "$.id");
        byte[] csvBytes = mvc.perform(get("/api/v1/reports/exports/" + csvId + "/download").cookie(s.cookie()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andReturn().getResponse().getContentAsByteArray();
        String text = new String(csvBytes, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(text).startsWith("﻿Recurso;Tipo;Ambiente").contains("Exportável").contains("excluídas");

        String xlsx = mvc.perform(post("/api/v1/reports/exports").with(csrf()).cookie(s.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"checks\",\"format\":\"xlsx\",\"period\":\"24h\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rowCount").value(1))
                .andReturn().getResponse().getContentAsString();
        String xlsxId = JsonPath.read(xlsx, "$.id");
        byte[] xlsxBytes = mvc.perform(get("/api/v1/reports/exports/" + xlsxId + "/download").cookie(s.cookie()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("spreadsheetml")))
                .andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
            var sheet = wb.getSheetAt(0);
            assertThat(sheet.getSheetName()).isEqualTo("Verificações");
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Recurso");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("Exportável");
            assertThat(sheet.getRow(1).getCell(2).getStringCellValue()).isEqualTo("sim");
        }

        mvc.perform(get("/api/v1/reports/exports").cookie(s.cookie()))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].createdBy").value("Admin"));
        mvc.perform(post("/api/v1/reports/exports").with(csrf()).cookie(s.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"availability\",\"format\":\"pdf\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/audit?action=report.exported").cookie(s.cookie()))
                .andExpect(jsonPath("$.totalItems", greaterThanOrEqualTo(2)));

        Session other = register("Outro", "Org Outra Export");
        mvc.perform(get("/api/v1/reports/exports/" + csvId + "/download").cookie(other.cookie())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/reports/exports").cookie(other.cookie())).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("célula CSV neutraliza fórmulas e escapa separadores")
    void csvInjectionIsNeutralized() {
        ExportService.Table t = new ExportService.Table(java.util.List.of("A", "B"),
                java.util.List.of(java.util.List.of("=cmd()", "com;ponto e vírgula"), java.util.List.of("-5", "aspas \"x\"")));
        String out = new String(ExportService.csv(t), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(out).contains("'=cmd();\"com;ponto e vírgula\"").contains("-5;\"aspas \"\"x\"\"\"");
    }
}
