import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { Sidebar } from '../../shared/components/sidebar/sidebar';
import { Topbar } from '../../shared/components/topbar/topbar';
import { SessionService } from '../../session/session.service';

@Component({
  selector: 'app-layout',
  imports: [Sidebar, Topbar, RouterOutlet],
  template: `
    @if (session.context()?.autenticado) {
      <div class="fs-layout"><app-sidebar /><div class="fs-area"><app-topbar />
        <main class="fs-conteudo"><router-outlet /></main>
      </div></div>
    }
  `,
})
export class AppLayout {
  readonly session = inject(SessionService);
}
