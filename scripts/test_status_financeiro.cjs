// Casos de negocio reais do sincronizador da interface, sem banco ou nuvem.
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const context = vm.createContext({ console, document: { getElementById: () => ({ value: 'aluno' }) } });
vm.runInContext(fs.readFileSync(path.join(__dirname, '../frontend/public/js/app.js'), 'utf8')
  .replace('const app = new GymApp();', 'globalThis.GymApp = GymApp;'), context);

async function conferir(status, mensalidades, esperado, { falhaConsulta = false, falhaAtualizacao = false } = {}) {
  const app = Object.create(context.GymApp.prototype);
  const aluno = { id: 'aluno', status };
  const gravacoes = [], avisos = [];
  app.alunos = [aluno];
  app.mostrarToast = value => avisos.push(value);
  app.apiRequest = async (_, options) => {
    if (options?.method === 'PATCH') {
      gravacoes.push(JSON.parse(options.body).status);
      return { ok: !falhaAtualizacao };
    }
    return { ok: !falhaConsulta, json: async () => mensalidades };
  };
  await app.sincronizarInadimplenciaAlunos();
  assert.equal(aluno.status, esperado);
  if (status === 'INATIVO' || falhaConsulta || (status === esperado && !falhaAtualizacao)) assert.equal(gravacoes.length, 0);
  if (falhaAtualizacao) assert.deepEqual(gravacoes, ['ATIVO']);
  if (falhaConsulta || falhaAtualizacao) assert.equal(avisos.length, 1);
}

(async () => {
  const vencida = { status: 'PENDENTE', dataVencimento: '2000-01-01' };
  const paga = { status: 'PAGO', dataVencimento: '2000-01-01' };
  const futura = { status: 'PENDENTE', dataVencimento: '2999-01-01' };
  await conferir('ATIVO', [vencida], 'INADIMPLENTE');
  await conferir('INADIMPLENTE', [paga], 'ATIVO');
  await conferir('INADIMPLENTE', [paga, vencida], 'INADIMPLENTE');
  await conferir('INADIMPLENTE', [paga, futura], 'ATIVO');
  await conferir('ATIVO', [{ status: 'ATRASADO' }], 'INADIMPLENTE');
  await conferir('ATIVO', [{ status: 'PENDENTE', dataVencimento: new Date().toISOString().split('T')[0] }], 'ATIVO');
  await conferir('INATIVO', [paga], 'INATIVO');
  await conferir('INADIMPLENTE', [], 'INADIMPLENTE', { falhaConsulta: true });
  await conferir('INADIMPLENTE', [paga], 'INADIMPLENTE', { falhaAtualizacao: true });
  console.log('[OK] Quitacao, dividas restantes, cobranca futura, inativo e falhas de comunicacao');

  const app = Object.create(context.GymApp.prototype);
  const fluxo = [];
  app.apiRequest = async () => ({ ok: true });
  app.mostrarToast = app.fecharModal = () => {};
  app.carregarMensalidadesDoAluno = async () => fluxo.push('mensalidades');
  app.carregarAlunos = async () => fluxo.push('alunos');
  await app.confirmarPagamento('mensalidade', 'PIX');
  assert.deepEqual(fluxo, ['mensalidades', 'alunos']);
  console.log('[OK] Pagamento atualiza imediatamente mensalidades e situacao dos alunos');
})().catch(error => { console.error(error); process.exitCode = 1; });
