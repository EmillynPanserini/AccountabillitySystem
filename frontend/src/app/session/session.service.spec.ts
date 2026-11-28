import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SessionContext, SessionService } from './session.service';
import { sessionGuard } from './session.guard';

const anonymous: SessionContext = { autenticado: false, identificador: null, capacidades: [], csrfToken: 'fixture-anon', csrfHeader: 'X-CSRF-TOKEN' };
const authenticated: SessionContext = { ...anonymous, autenticado: true, identificador: 'admin-ficticio', capacidades: ['ADMINISTRAR_IDENTIDADES', 'CRIAR_EMPRESA'], csrfToken: 'fixture-auth' };
const settle = async () => { await Promise.resolve(); await Promise.resolve(); };

describe('contrato de sessão', () => {
  let service: SessionService;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    service = TestBed.inject(SessionService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => { http.verify(); TestBed.resetTestingModule(); vi.useRealTimers(); });

  async function load(context: SessionContext): Promise<void> {
    const pending = service.refresh();
    http.expectOne('/api/v1/sessao').flush(context);
    await pending;
  }
  async function startLogin(): Promise<{ result: Promise<boolean> }> {
    const result = service.login('admin-ficticio', 'senha-ficticia');
    http.expectOne('/api/v1/sessao').flush(anonymous);
    await settle();
    return { result };
  }

  it('envia formulário com CSRF somente à API relativa e renova contexto após login', async () => {
    const { result } = await startLogin();
    const post = http.expectOne('/api/v1/login');
    expect(post.request.method).toBe('POST');
    expect(post.request.body.get('identificador')).toBe('admin-ficticio');
    expect(post.request.body.get('senha')).toBe('senha-ficticia');
    expect(post.request.headers.get('X-CSRF-TOKEN')).toBe(anonymous.csrfToken);
    post.flush(null, { status: 204, statusText: 'No Content' });
    await settle();
    http.expectOne('/api/v1/sessao').flush(authenticated);
    expect(await result).toBe(true);
    expect(service.context()?.capacidades).toEqual(authenticated.capacidades);
    expect(service.context()?.csrfToken).toBe(authenticated.csrfToken);
  });

  it.each([401, 429, 0])('trata erro %i sem repetir login', async status => {
    const { result } = await startLogin();
    const request = http.expectOne('/api/v1/login');
    if (status === 0) request.error(new ProgressEvent('error'));
    else request.flush({}, { status, statusText: 'Rejected' });
    expect(await result).toBe(false);
    expect(service.context()).toBeNull();
    expect(service.message()).toContain(status === 401 ? 'inválidos' : status === 429 ? '15 minutos' : 'conectar');
    http.expectNone('/api/v1/login');
  });

  it('renova CSRF recusado sem repetir mutação nem restaurar acesso privado', async () => {
    const { result } = await startLogin();
    http.expectOne('/api/v1/login').flush({}, { status: 403, statusText: 'Forbidden' });
    await settle();
    http.expectOne('/api/v1/sessao').flush({ ...anonymous, csrfToken: 'fixture-new' });
    expect(await result).toBe(false);
    expect(service.context()?.csrfToken).toBe('fixture-new');
    expect(service.needsLogin()).toBe(true);
    http.expectNone('/api/v1/login');
  });

  it('logout limpa acesso imediatamente e ignora resposta antiga de sessão', async () => {
    await load(authenticated);
    const old = service.refresh();
    const stale = http.expectOne('/api/v1/sessao');
    const logout = service.logout();
    expect(service.context()).toBeNull();
    const post = http.expectOne('/api/v1/logout');
    expect(post.request.headers.get('X-CSRF-TOKEN')).toBe(authenticated.csrfToken);
    post.flush(null, { status: 204, statusText: 'No Content' });
    await settle();
    http.expectOne('/api/v1/sessao').flush(anonymous);
    expect(await logout).toBe(true);
    stale.flush(authenticated);
    expect(await old).toBeNull();
    expect(service.context()?.autenticado).toBe(false);
    expect(service.logoutPending()).toBe(false);
  });

  it('saída com falha informa sessão possivelmente ativa e permite nova ação explícita', async () => {
    await load(authenticated);
    const pending = service.logout();
    http.expectOne('/api/v1/logout').error(new ProgressEvent('error'));
    expect(await pending).toBe(false);
    expect(service.logoutPending()).toBe(true);
    expect(service.message()).toContain('pode continuar ativa');
    expect(service.context()).toBeNull();
  });

  it('guard consulta servidor e recusa sessão expirada mesmo com contexto anterior', async () => {
    await load(authenticated);
    const result = TestBed.runInInjectionContext(() => sessionGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot));
    http.expectOne('/api/v1/sessao').flush(anonymous);
    expect((await result as UrlTree).toString()).toBe('/login');
    expect(service.needsLogin()).toBe(true);
  });

  it('guard permite sessão confirmada pelo servidor', async () => {
    const result = TestBed.runInInjectionContext(() => sessionGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot));
    http.expectOne('/api/v1/sessao').flush(authenticated);
    expect(await result).toBe(true);
  });

  it('limpa a interface após 30 minutos sem atividade sem reenviar operações', async () => {
    vi.useFakeTimers();
    await load(authenticated);
    vi.advanceTimersByTime(30 * 60 * 1000);
    expect(service.context()).toBeNull();
    expect(service.message()).toContain('expirou');
    http.expectNone('/api/v1/login');
    http.expectNone('/api/v1/logout');
  });
});
