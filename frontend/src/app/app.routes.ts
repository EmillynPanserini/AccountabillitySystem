import { Routes } from '@angular/router';
import { AppLayout } from './layouts/app-layout/app-layout';
import { Login } from './pages/login/login';
import { Home } from './pages/home/home';
import { sessionGuard } from './session/session.guard';

export const routes: Routes = [
  { path: 'login', component: Login },
  { path: 'home', component: AppLayout, canActivate: [sessionGuard], children: [{ path: '', component: Home }] },
  { path: '**', redirectTo: 'login' },
];
