import { Component } from '@angular/core';

/**
 * Minimal Angular shell. Production MVP currently serves the same-origin static UI
 * from Spring (`aplicacion/src/main/resources/static`). This TypeScript source
 * documents the intended Angular strict client.
 */
@Component({
  selector: 'fia-root',
  standalone: true,
  template: `
    <header>
      <h1>Financial Intelligence Agent</h1>
      <p>Darwin Mora · sesión local · sin pagos reales</p>
    </header>
  `,
})
export class AppComponent {}
