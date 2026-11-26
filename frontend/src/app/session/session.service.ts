import { HttpClient, HttpErrorResponse, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable, OnDestroy, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

export interface SessionContext {
  autenticado: boolean;
  identificador: string | null;
  capacidades: string[];
  csrfToken: string;
  csrfHeader: string;
}

@Injectable({ providedIn: 'root' })
export class SessionService implements OnDestroy {
  private readonly http = inject(HttpClient);
  readonly context = signal<SessionContext | null>(null);
  readonly busy = signal(false);
  readonly message = signal('');
  readonly needsLogin = signal(false);
  readonly logoutPending = signal(false);
  private generation = 0;
  private expiry?: ReturnType<typeof setTimeout>;

  ngOnDestroy(): void { clearTimeout(this.expiry); }

  async refresh(): Promise<SessionContext | null> {
    if (this.busy()) return null;
    const generation = ++this.generation;
    try {
      const context = await this.readContext(generation);
      if (!context || (this.needsLogin() && context.autenticado)) return null;
      this.commit(context);
      return context;
    } catch (error) {
      if (generation === this.generation) this.clear(this.errorMessage(error));
      return null;
    }
  }

  async login(identifier: string, password: string): Promise<boolean> {
    if (this.busy()) return false;
    const generation = ++this.generation;
    this.busy.set(true);
    this.message.set('');
    try {
      const before = await this.readContext(generation);
      if (!before) return false;
      const body = new HttpParams().set('identificador', identifier).set('senha', password);
      await firstValueFrom(this.http.post<void>('/api/v1/login', body, { headers: this.csrfHeaders(before) }));
      const after = await this.readContext(generation);
      if (!after) return false;
      if (!after.autenticado) throw new Error('Autenticação não confirmada.');
      this.needsLogin.set(false);
      this.logoutPending.set(false);
      this.commit(after);
      return true;
    } catch (error) {
      await this.handleFailure(error, generation, 'login');
      return false;
    } finally {
      if (generation === this.generation) this.busy.set(false);
    }
  }

  async logout(): Promise<boolean> {
    if (this.busy()) return false;
    const previous = this.context();
    const generation = ++this.generation;
    this.clear('');
    this.logoutPending.set(true);
    this.busy.set(true);
    try {
      const before = previous ?? await this.readContext(generation);
      if (!before) return false;
      await firstValueFrom(this.http.post<void>('/api/v1/logout', null, { headers: this.csrfHeaders(before) }));
      const after = await this.readContext(generation);
      if (!after) return false;
      if (after.autenticado) throw new Error('Saída não confirmada.');
      this.logoutPending.set(false);
      this.commit(after);
      return true;
    } catch (error) {
      await this.handleFailure(error, generation, 'logout');
      return false;
    } finally {
      if (generation === this.generation) this.busy.set(false);
    }
  }

  private async readContext(generation: number): Promise<SessionContext | null> {
    const context = await firstValueFrom(this.http.get<SessionContext>('/api/v1/sessao'));
    return generation === this.generation ? context : null;
  }

  private csrfHeaders(context: SessionContext): HttpHeaders {
    // Somente os dois POSTs relativos acima recebem o token; não há interceptor para destinos arbitrários.
    return new HttpHeaders({ 'X-CSRF-TOKEN': context.csrfToken });
  }

  private commit(context: SessionContext): void {
    clearTimeout(this.expiry);
    this.context.set(context);
    if (context.autenticado) {
      this.expiry = setTimeout(() => {
        ++this.generation;
        this.clear('Sua sessão expirou. Entre novamente para continuar.');
      }, 30 * 60 * 1000);
    } else {
      this.needsLogin.set(true);
    }
  }

  private clear(message: string): void {
    clearTimeout(this.expiry);
    this.context.set(null);
    this.needsLogin.set(true);
    this.message.set(message);
  }

  private async handleFailure(error: unknown, generation: number, operation: 'login' | 'logout'): Promise<void> {
    if (generation !== this.generation) return;
    this.clear(operation === 'logout'
      ? 'Não foi possível confirmar a saída. A sessão pode continuar ativa no servidor; tente sair novamente.'
      : this.errorMessage(error, true));
    if (error instanceof HttpErrorResponse && error.status === 403) {
      // Renova o contexto sem repetir a mutação recusada e sem restaurar acesso privado.
      try {
        const context = await this.readContext(generation);
        if (context && !context.autenticado) this.commit(context);
      } catch { /* A mensagem original permanece; a próxima ação consulta a sessão novamente. */ }
    }
  }

  private errorMessage(error: unknown, login = false): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 401) return login ? 'Identificador ou senha inválidos.' : 'Sua sessão expirou. Entre novamente.';
      if (error.status === 403) return 'Não foi possível validar a sessão. Tente novamente; nenhuma operação foi repetida.';
      if (error.status === 429) return 'Muitas tentativas. Aguarde 15 minutos antes de tentar novamente.';
    }
    return 'Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.';
  }
}
