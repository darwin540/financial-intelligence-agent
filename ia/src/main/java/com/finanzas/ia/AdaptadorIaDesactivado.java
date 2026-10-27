package com.finanzas.ia;

/**
 * MVP AI adapter: present and disabled. No provider, no egress, no OCR.
 */
public final class AdaptadorIaDesactivado {

  public static final boolean HABILITADO = false;

  public boolean estaHabilitado() {
    return false;
  }

  public void asegurarDesactivado() {
    if (HABILITADO) {
      throw new IllegalStateException("LLM_NO_DISPONIBLE_EN_ESTA_FASE");
    }
  }

  /**
   * @return always empty — no network call is made
   */
  public String redactar(String corridaId) {
    asegurarDesactivado();
    throw new UnsupportedOperationException("LLM_NO_DISPONIBLE_EN_ESTA_FASE");
  }
}
