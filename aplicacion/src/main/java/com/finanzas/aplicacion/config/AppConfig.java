package com.finanzas.aplicacion.config;

import com.finanzas.aplicacion.seguridad.DocumentStore;
import com.finanzas.aplicacion.seguridad.LocalSessionFilter;
import com.finanzas.aplicacion.seguridad.ProhibitedFieldFilter;
import com.finanzas.ia.AdaptadorIaDesactivado;
import com.finanzas.optimizacion.PriorizacionEngine;
import com.finanzas.proyecciones.AmortizacionEngine;
import com.finanzas.reportes.ReporteEjecutivoBuilder;
import java.nio.file.Path;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
@EnableConfigurationProperties(FinanzasProperties.class)
public class AppConfig {

  @Bean
  PriorizacionEngine priorizacionEngine() {
    return new PriorizacionEngine();
  }

  @Bean
  AmortizacionEngine amortizacionEngine() {
    return new AmortizacionEngine();
  }

  @Bean
  ReporteEjecutivoBuilder reporteEjecutivoBuilder() {
    return new ReporteEjecutivoBuilder();
  }

  @Bean
  AdaptadorIaDesactivado adaptadorIaDesactivado() {
    return new AdaptadorIaDesactivado();
  }

  @Bean
  DocumentStore documentStore(FinanzasProperties props) {
    return new DocumentStore(
        Path.of(props.getAlmacenDocumentos()), Path.of(props.getClaveCifradoPath()));
  }

  @Bean
  FilterRegistrationBean<LocalSessionFilter> sessionFilter(LocalSessionFilter filter) {
    FilterRegistrationBean<LocalSessionFilter> bean = new FilterRegistrationBean<>();
    bean.setFilter(filter);
    bean.addUrlPatterns("/api/*");
    bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
    return bean;
  }

  @Bean
  FilterRegistrationBean<ProhibitedFieldFilter> prohibitedFilter(ProhibitedFieldFilter filter) {
    FilterRegistrationBean<ProhibitedFieldFilter> bean = new FilterRegistrationBean<>();
    bean.setFilter(filter);
    bean.addUrlPatterns("/api/*");
    bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return bean;
  }
}
