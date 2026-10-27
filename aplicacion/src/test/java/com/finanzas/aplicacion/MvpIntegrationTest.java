package com.finanzas.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class MvpIntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;

  private MockCookie session;

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    Path tmp = Path.of(System.getProperty("java.io.tmpdir"), "finanzas-it");
    r.add("finanzas.almacen-documentos", () -> tmp.resolve("docs").toString());
    r.add("finanzas.clave-cifrado-path", () -> tmp.resolve("key.bin").toString());
    r.add("finanzas.usuario-local-secreto", () -> "cambiar-en-local");
    r.add("spring.datasource.url", () -> "jdbc:postgresql://127.0.0.1:5432/finanzas");
    r.add("spring.datasource.username", () -> "finanzas");
    r.add("spring.datasource.password", () -> "finanzas_dev");
  }

  @BeforeEach
  void login() throws Exception {
    MvcResult res =
        mvc.perform(
                post("/api/v1/sesion")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Finanzas-Solicitud", "1")
                    .content("{\"nombre\":\"darwin\",\"secreto\":\"cambiar-en-local\"}"))
            .andExpect(status().isNoContent())
            .andReturn();
    String set = res.getResponse().getHeader("Set-Cookie");
    String token = set.split(";")[0].split("=")[1];
    session = new MockCookie("FINANZAS_SESION", token);
  }

  @Test
  void saludPublica() throws Exception {
    mvc.perform(get("/api/v1/salud")).andExpect(status().isOk());
  }

  @Test
  void rutasDinero404() throws Exception {
    mvc.perform(
            post("/api/v1/pagos")
                .cookie(session)
                .header("X-Finanzas-Solicitud", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/v1/transferencias")
                .cookie(session)
                .header("X-Finanzas-Solicitud", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/v1/creditos")
                .cookie(session)
                .header("X-Finanzas-Solicitud", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/v1/simulaciones")
                .cookie(session)
                .header("X-Finanzas-Solicitud", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void campoProhibido400SinAuditarValor() throws Exception {
    MvcResult res =
        mvc.perform(
                post("/api/v1/cuentas")
                    .cookie(session)
                    .header("X-Finanzas-Solicitud", "1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"alias\":\"x\",\"moneda\":\"COP\",\"pin\":\"1234\"}"))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertTrue(res.getResponse().getContentAsString().contains("CAMPO_PROHIBIDO"));
    assertFalse(res.getResponse().getContentAsString().contains("1234"));
  }

  @Test
  void llmNoSeActiva() throws Exception {
    MvcResult res =
        mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                        "/api/v1/configuracion")
                    .cookie(session)
                    .header("X-Finanzas-Solicitud", "1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"llmHabilitado\":true}"))
            .andExpect(status().isUnprocessableEntity())
            .andReturn();
    assertTrue(res.getResponse().getContentAsString().contains("LLM_NO_DISPONIBLE_EN_ESTA_FASE"));
  }

  @Test
  void csvRevisionFlujoEstrategiasReporte() throws Exception {
    byte[] csv =
        Files.readAllBytes(
            Path.of("src/test/resources/extracto-sintetico.csv"));
    MockMultipartFile file =
        new MockMultipartFile(
            "archivo", "extracto-sintetico.csv", "text/csv", csv);

    MvcResult up =
        mvc.perform(
                multipart("/api/v1/documentos")
                    .file(file)
                    .param("tipo", "EXTRACTO_CUENTA")
                    .cookie(session)
                    .header("X-Finanzas-Solicitud", "1"))
            .andExpect(status().isCreated())
            .andReturn();
    String docId = mapper.readTree(up.getResponse().getContentAsString()).get("documentoId").asText();

    mvc.perform(
            post("/api/v1/documentos/" + docId + "/extraccion")
                .cookie(session)
                .header("X-Finanzas-Solicitud", "1"))
        .andExpect(status().isAccepted());

    MvcResult revs =
        mvc.perform(get("/api/v1/revisiones?estado=ABIERTA").cookie(session))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode elementos = mapper.readTree(revs.getResponse().getContentAsString()).get("elementos");
    assertTrue(elementos.size() >= 1);

    for (JsonNode el : elementos) {
      mvc.perform(
              post("/api/v1/revisiones/" + el.get("revisionId").asText() + "/decision")
                  .cookie(session)
                  .header("X-Finanzas-Solicitud", "1")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"decision\":\"CONFIRMAR\",\"clasificacion\":\"GASTO_NO_ESENCIAL\"}"))
          .andExpect(status().isOk());
    }

    // second overlapping extract should link evidence
    MvcResult up2 =
        mvc.perform(
                multipart("/api/v1/documentos")
                    .file(
                        new MockMultipartFile(
                            "archivo",
                            "extracto-b.csv",
                            "text/csv",
                            "fecha,descripcion,monto,direccion,referencia,moneda\n2026-10-20,Compra  supermercado,45320.50,DEBITO,AB12,COP\n"
                                .getBytes(StandardCharsets.UTF_8)))
                    .param("tipo", "EXTRACTO_CUENTA")
                    .cookie(session)
                    .header("X-Finanzas-Solicitud", "1"))
            .andExpect(status().isCreated())
            .andReturn();
    String doc2 = mapper.readTree(up2.getResponse().getContentAsString()).get("documentoId").asText();
    mvc.perform(
            post("/api/v1/documentos/" + doc2 + "/extraccion")
                .cookie(session)
                .header("X-Finanzas-Solicitud", "1"))
        .andExpect(status().isAccepted());

    MvcResult revs2 =
        mvc.perform(get("/api/v1/revisiones?estado=ABIERTA").cookie(session)).andReturn();
    for (JsonNode el : mapper.readTree(revs2.getResponse().getContentAsString()).get("elementos")) {
      MvcResult dec =
          mvc.perform(
                  post("/api/v1/revisiones/" + el.get("revisionId").asText() + "/decision")
                      .cookie(session)
                      .header("X-Finanzas-Solicitud", "1")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"decision\":\"CONFIRMAR\"}"))
              .andExpect(status().isOk())
              .andReturn();
      JsonNode body = mapper.readTree(dec.getResponse().getContentAsString());
      if (body.has("montoContadoUnaVez")) {
        assertEquals("45320.50", body.get("montoContadoUnaVez").asText());
      }
    }

    MvcResult flujo =
        mvc.perform(
                post("/api/v1/flujo-caja/calcular")
                    .cookie(session)
                    .header("X-Finanzas-Solicitud", "1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "moneda":"COP","desde":"2026-11-01","hasta":"2026-11-30",
                          "saldoApertura":"1000000.00","piso":"250000.00",
                          "esencialesRegistrados":true,
                          "eventos":[
                            {"fecha":"2026-11-05","direccion":"SALIDA","monto":"800000.00","concepto":"Arriendo"},
                            {"fecha":"2026-11-12","direccion":"SALIDA","monto":"300000.00","concepto":"Minimo"},
                            {"fecha":"2026-11-28","direccion":"ENTRADA","monto":"2500000.00","concepto":"Nomina"}
                          ]
                        }
                        """))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode flujoBody = mapper.readTree(flujo.getResponse().getContentAsString());
    assertFalse(flujoBody.get("viable").asBoolean());
    assertEquals("0.00", flujoBody.get("disponibleParaComprometer").asText());
    String corridaFlujo = flujoBody.get("corridaId").asText();

    // EST-01 style obligations + comparison
    String[] payloads =
        new String[] {
          "{\"alias\":\"D1\",\"tipo\":\"CONSUMO\",\"moneda\":\"COP\",\"saldoTotal\":\"2000000.00\",\"pagoMinimo\":\"100000.00\",\"cuotaContractual\":\"100000.00\",\"tasa\":{\"valor\":\"0.03000000\",\"periodo\":\"MENSUAL\",\"base\":\"EFECTIVA\"},\"diaVencimiento\":28,\"efectoAbono\":\"REDUCE_PLAZO\"}",
          "{\"alias\":\"D2\",\"tipo\":\"CONSUMO\",\"moneda\":\"COP\",\"saldoTotal\":\"250000.00\",\"pagoMinimo\":\"100000.00\",\"cuotaContractual\":\"100000.00\",\"tasa\":{\"valor\":\"0.01000000\",\"periodo\":\"MENSUAL\",\"base\":\"EFECTIVA\"},\"diaVencimiento\":28,\"efectoAbono\":\"REDUCE_PLAZO\"}",
          "{\"alias\":\"D3\",\"tipo\":\"CONSUMO\",\"moneda\":\"COP\",\"saldoTotal\":\"500000.00\",\"pagoMinimo\":\"200000.00\",\"cuotaContractual\":\"200000.00\",\"tasa\":{\"valor\":\"0.02500000\",\"periodo\":\"MENSUAL\",\"base\":\"EFECTIVA\"},\"diaVencimiento\":28,\"efectoAbono\":\"REDUCE_PLAZO\"}"
        };
    StringBuilder ids = new StringBuilder("[");
    for (int i = 0; i < payloads.length; i++) {
      MvcResult o =
          mvc.perform(
                  post("/api/v1/obligaciones")
                      .cookie(session)
                      .header("X-Finanzas-Solicitud", "1")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(payloads[i]))
              .andExpect(status().isOk())
              .andReturn();
      if (i > 0) {
        ids.append(",");
      }
      ids.append("\"")
          .append(mapper.readTree(o.getResponse().getContentAsString()).get("obligacionId").asText())
          .append("\"");
    }
    ids.append("]");

    MvcResult cmp =
        mvc.perform(
                post("/api/v1/estrategias/comparaciones")
                    .cookie(session)
                    .header("X-Finanzas-Solicitud", "1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"objetivo\":\"MENOR_INTERES_TOTAL\",\"moneda\":\"COP\",\"horizonteInicio\":\"2026-11-28\",\"presupuestoDeudaMensual\":\"550000.00\",\"presupuestoEsSupuesto\":true,\"obligacionIds\":"
                            + ids
                            + "}"))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode estrategias = mapper.readTree(cmp.getResponse().getContentAsString()).get("estrategias");
    assertEquals("260294.38", estrategias.get(0).get("interesTotal").asText());
    assertTrue(estrategias.get(0).get("esRecomendada").asBoolean());

    MvcResult rep =
        mvc.perform(get("/api/v1/reportes/ejecutivo").cookie(session))
            .andExpect(status().isOk())
            .andReturn();
    assertTrue(rep.getResponse().getContentAsString().contains("corridaId"));
    assertTrue(corridaFlujo.length() > 0);
  }

  @Test
  void configRechazaTipoCambioReutilizable() throws Exception {
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                    "/api/v1/configuracion")
                .cookie(session)
                .header("X-Finanzas-Solicitud", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipoCambioCopUsd\":\"4000.00\"}"))
        .andExpect(status().isBadRequest());
  }
}
