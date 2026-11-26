import { Component, inject } from '@angular/core';
import { SessionService } from '../../../session/session.service';

@Component({ selector: 'app-topbar', templateUrl: './topbar.html', styleUrl: './topbar.css' })
export class Topbar {
  readonly session = inject(SessionService);
}
