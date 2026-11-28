import { Component, inject, OnInit } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { Router } from '@angular/router';
import { SessionService } from '../../session/session.service';

@Component({
  selector: 'app-login',
  imports: [FormsModule],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login implements OnInit {
  readonly session = inject(SessionService);
  private readonly router = inject(Router);
  identificador = '';
  senha = '';
  mostrarSenha = false;

  async ngOnInit(): Promise<void> {
    const context = await this.session.refresh();
    if (context?.autenticado) await this.router.navigateByUrl('/home');
  }

  async entrar(formulario: NgForm): Promise<void> {
    if (formulario.invalid || this.session.busy()) {
      formulario.form.markAllAsTouched();
      return;
    }
    const password = this.senha;
    this.senha = '';
    this.mostrarSenha = false;
    if (await this.session.login(this.identificador, password)) await this.router.navigateByUrl('/home');
  }
}
