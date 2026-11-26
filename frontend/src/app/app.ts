import { Component, effect, inject } from '@angular/core';
import { Router, RouterOutlet } from '@angular/router';
import { SessionService } from './session/session.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  template: '<router-outlet />',
})
export class App {
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);
  constructor() {
    effect(() => {
      if (this.session.needsLogin() && this.router.url !== '/login') void this.router.navigateByUrl('/login');
    });
  }
}
