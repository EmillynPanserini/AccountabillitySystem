import { Component, inject } from '@angular/core';
import { SessionService } from '../../session/session.service';

@Component({
  selector: 'app-home',
  template: `
    <header class="fs-page-header"><small>FINANSYS</small><h1>Visão geral</h1>
      <p>Bem-vindo, {{ session.context()?.identificador }}.</p></header>
    <section class="session-card" aria-labelledby="session-title">
      <h2 id="session-title">Sua conta</h2><p>Sessão autenticada.</p>
      <ul>@for (capability of session.context()?.capacidades; track capability) {
        <li>{{ capability === 'ADMINISTRAR_IDENTIDADES' ? 'Administrar identidades' : capability === 'CRIAR_EMPRESA' ? 'Criar empresa' : capability }}</li>
      }</ul>
      <p>As áreas contábeis e de empresas ainda estão indisponíveis.</p>
      <button class="fs-botao" (click)="session.refresh()" [disabled]="session.busy()">Verificar sessão</button>
    </section>
  `,
  styles: `.session-card { max-width: 680px; padding: 24px; border: 1px solid var(--fs-borda); border-radius: 12px; background: white; }
    h2 { margin-top: 0; font-size: 18px; } p, li { font-size: 14px; line-height: 1.7; }`,
})
export class Home { readonly session = inject(SessionService); }
