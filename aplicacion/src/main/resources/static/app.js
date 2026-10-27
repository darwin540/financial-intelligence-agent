const headersMut = { "Content-Type": "application/json", "X-Finanzas-Solicitud": "1" };

async function api(path, opts = {}) {
  const res = await fetch(path, { credentials: "same-origin", ...opts });
  const text = await res.text();
  let body = null;
  try { body = text ? JSON.parse(text) : null; } catch { body = text; }
  return { ok: res.ok, status: res.status, body };
}

document.getElementById("btnSesion").onclick = async () => {
  const r = await api("/api/v1/sesion", {
    method: "POST",
    headers: headersMut,
    body: JSON.stringify({ nombre: "darwin", secreto: "cambiar-en-local" })
  });
  document.getElementById("salud").textContent = r.status === 204
    ? "Sesión local abierta"
    : "No se pudo abrir sesión (" + r.status + ")";
  cargarConfig();
  cargarRevisiones();
};

document.getElementById("archivo").onchange = async (e) => {
  const file = e.target.files[0];
  if (!file) return;
  const fd = new FormData();
  fd.append("archivo", file);
  fd.append("tipo", "EXTRACTO_CUENTA");
  const up = await fetch("/api/v1/documentos", {
    method: "POST",
    credentials: "same-origin",
    headers: { "X-Finanzas-Solicitud": "1" },
    body: fd
  });
  const doc = await up.json();
  if (!up.ok) {
    document.getElementById("salida").textContent = JSON.stringify(doc, null, 2);
    return;
  }
  const ex = await api("/api/v1/documentos/" + doc.documentoId + "/extraccion", {
    method: "POST",
    headers: headersMut
  });
  document.getElementById("salida").textContent = JSON.stringify(ex.body, null, 2);
  document.getElementById("btnConfirmarTodo").disabled = false;
  cargarRevisiones();
};

document.getElementById("btnConfirmarTodo").onclick = async () => {
  const rev = await api("/api/v1/revisiones?estado=ABIERTA");
  for (const item of rev.body.elementos || []) {
    await api("/api/v1/revisiones/" + item.revisionId + "/decision", {
      method: "POST",
      headers: headersMut,
      body: JSON.stringify({ decision: "CONFIRMAR", clasificacion: "GASTO_NO_ESENCIAL" })
    });
  }
  cargarRevisiones();
};

document.getElementById("btnFlujo").onclick = async () => {
  await api("/api/v1/configuracion", {
    method: "PUT",
    headers: headersMut,
    body: JSON.stringify({ minimoSeguridadCop: "250000.00" })
  });
  const r = await api("/api/v1/flujo-caja/calcular", {
    method: "POST",
    headers: headersMut,
    body: JSON.stringify({
      moneda: "COP",
      desde: "2026-11-01",
      hasta: "2026-11-30",
      saldoApertura: "1000000.00",
      piso: "250000.00",
      esencialesRegistrados: true,
      eventos: [
        { fecha: "2026-11-05", direccion: "SALIDA", monto: "800000.00", concepto: "Arriendo" },
        { fecha: "2026-11-12", direccion: "SALIDA", monto: "300000.00", concepto: "Minimo" },
        { fecha: "2026-11-28", direccion: "ENTRADA", monto: "2500000.00", concepto: "Nomina" }
      ]
    })
  });
  document.getElementById("salida").textContent = JSON.stringify(r.body, null, 2);
};

document.getElementById("btnEst").onclick = async () => {
  const ids = [];
  const deudas = [
    { alias: "Consumo largo", saldoTotal: "2000000.00", cuotaContractual: "100000.00", tasa: { valor: "0.03000000", periodo: "MENSUAL", base: "EFECTIVA" }, idHint: "d1000000-0000-4000-8000-000000000001" },
    { alias: "Saldo menor", saldoTotal: "250000.00", cuotaContractual: "100000.00", tasa: { valor: "0.01000000", periodo: "MENSUAL", base: "EFECTIVA" }, idHint: "d1000000-0000-4000-8000-000000000002" },
    { alias: "Cuota alta", saldoTotal: "500000.00", cuotaContractual: "200000.00", tasa: { valor: "0.02500000", periodo: "MENSUAL", base: "EFECTIVA" }, idHint: "d1000000-0000-4000-8000-000000000003" }
  ];
  for (const d of deudas) {
    const r = await api("/api/v1/obligaciones", {
      method: "POST",
      headers: headersMut,
      body: JSON.stringify({
        alias: d.alias,
        tipo: "CONSUMO",
        moneda: "COP",
        saldoTotal: d.saldoTotal,
        pagoMinimo: d.cuotaContractual,
        cuotaContractual: d.cuotaContractual,
        tasa: { valor: d.tasa.valor, expresion: "FRACCION_POR_PERIODO", periodo: d.tasa.periodo, base: d.tasa.base },
        diaVencimiento: 28,
        efectoAbono: "REDUCE_PLAZO"
      })
    });
    ids.push(r.body.obligacionId);
  }
  const cmp = await api("/api/v1/estrategias/comparaciones", {
    method: "POST",
    headers: headersMut,
    body: JSON.stringify({
      objetivo: "MENOR_INTERES_TOTAL",
      moneda: "COP",
      horizonteInicio: "2026-11-28",
      presupuestoDeudaMensual: "550000.00",
      presupuestoEsSupuesto: true,
      efectoAbono: "REDUCE_PLAZO",
      obligacionIds: ids
    })
  });
  document.getElementById("salida").textContent = JSON.stringify(cmp.body, null, 2);
};

document.getElementById("btnReporte").onclick = async () => {
  const r = await api("/api/v1/reportes/ejecutivo");
  document.getElementById("reporte").textContent = JSON.stringify(r.body, null, 2);
};

async function cargarConfig() {
  const r = await api("/api/v1/configuracion");
  if (r.ok) {
    document.getElementById("config").textContent =
      "Monedas: " + r.body.monedas.join(", ") +
      " · FX: " + (r.body.fx && r.body.fx.modo) +
      " · IA: " + (r.body.llmHabilitado ? "on" : "off");
  }
}

async function cargarRevisiones() {
  const r = await api("/api/v1/revisiones?estado=ABIERTA");
  const ul = document.getElementById("revisiones");
  ul.innerHTML = "";
  for (const item of (r.body && r.body.elementos) || []) {
    const li = document.createElement("li");
    li.textContent = item.asunto + " — " + item.motivo;
    ul.appendChild(li);
  }
  document.getElementById("btnConfirmarTodo").disabled = !((r.body && r.body.total) > 0);
}

(async () => {
  const s = await api("/api/v1/salud");
  document.getElementById("salud").textContent = s.ok
    ? "API " + s.body.versionApi + " · " + s.body.estado
    : "API no disponible";
})();
