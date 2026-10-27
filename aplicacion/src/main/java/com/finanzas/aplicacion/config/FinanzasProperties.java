package com.finanzas.aplicacion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "finanzas")
public class FinanzasProperties {

  private String almacenDocumentos;
  private String claveCifradoPath;
  private String usuarioLocalNombre = "darwin";
  private String usuarioLocalSecreto;
  private boolean llmHabilitado = false;

  public String getAlmacenDocumentos() {
    return almacenDocumentos;
  }

  public void setAlmacenDocumentos(String almacenDocumentos) {
    this.almacenDocumentos = almacenDocumentos;
  }

  public String getClaveCifradoPath() {
    return claveCifradoPath;
  }

  public void setClaveCifradoPath(String claveCifradoPath) {
    this.claveCifradoPath = claveCifradoPath;
  }

  public String getUsuarioLocalNombre() {
    return usuarioLocalNombre;
  }

  public void setUsuarioLocalNombre(String usuarioLocalNombre) {
    this.usuarioLocalNombre = usuarioLocalNombre;
  }

  public String getUsuarioLocalSecreto() {
    return usuarioLocalSecreto;
  }

  public void setUsuarioLocalSecreto(String usuarioLocalSecreto) {
    this.usuarioLocalSecreto = usuarioLocalSecreto;
  }

  public boolean isLlmHabilitado() {
    return llmHabilitado;
  }

  public void setLlmHabilitado(boolean llmHabilitado) {
    this.llmHabilitado = llmHabilitado;
  }
}
