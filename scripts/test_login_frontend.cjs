// Verifica comportamentos do JavaScript real, sem acessar dados da aplicacao.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const nodes = new Map();
const node = id => {
  if (!nodes.has(id)) nodes.set(id, {
    disabled: false, textContent: '', innerHTML: '',
    classList: { add() {}, remove() {} }, reset() {},
  });
  return nodes.get(id);
};
const context = vm.createContext({
  window: { addEventListener() {} },
  document: { getElementById: node, addEventListener() {} },
  Response, AbortController, setTimeout, clearTimeout, console,
});
function load(name) {
  return fs.readFileSync(path.join(root, 'frontend/public/js', name), 'utf8');
}
vm.runInContext(load('http.js'), context);
context.GymHttp = context.window.GymHttp;
vm.runInContext(load('app.js').replace('const app = new GymApp();', 'globalThis.GymApp = GymApp;'), context);
vm.runInContext(load('aluno.js') + '\nglobalThis.AlunoApp = AlunoApp;', context);

(async () => {
  context.fetch = async () => new Response('<html>proxy interno</html>', { status: 503 });
  let response = await context.GymHttp.request('/api/listarAlunos');
  assert.equal(response.status, 503);
  assert.match((await response.json()).mensagem, /Tente novamente/);
  context.fetch = async () => { throw new TypeError('detalhe tecnico da rede'); };
  await assert.rejects(context.GymHttp.request('/api/listarAlunos'), /Verifique sua conex/);
  console.log('[OK] Falhas HTTP e de rede recebem mensagens amigaveis');

  const admin = Object.create(context.GymApp.prototype);
  let storedToken = null;
  admin.setToken = value => { storedToken = value; };
  admin.mostrarToast = admin.fecharModal = () => {};
  admin.carregarAlunos = async () => {};
  context.fetch = async () => new Response('{}', { status: 401 });
  await admin.realizarLogin('gestao@example.com', 'incorreta');
  assert.equal(storedToken, null);
  assert.match(node('alerta-login').textContent, /senha incorretos/);
  assert.equal(node('btn-do-login').disabled, false);
  context.fetch = async (_, options) => {
    assert.equal(JSON.parse(options.body).perfil, 'ADMIN');
    return Response.json({ perfil: 'ADMIN', token: 'jwt-de-teste', usuario: 'gestao@example.com' });
  };
  await admin.realizarLogin('gestao@example.com', 'somente-teste');
  assert.equal(storedToken, 'jwt-de-teste');
  console.log('[OK] Login da gestao rejeita senha errada e aceita resposta autenticada');

  const aluno = Object.create(context.AlunoApp.prototype);
  aluno.mostrarToast = aluno.carregarDadosDoAluno = () => {};
  context.fetch = async () => new Response('{}', { status: 401 });
  await aluno.login('aluno@example.com', 'incorreta');
  assert.equal(aluno.token, undefined);
  assert.match(node('alerta-login-aluno').textContent, /senha incorretos/);
  assert.equal(node('btn-entrar-aluno').disabled, false);
  console.log('[OK] Login do aluno rejeita senha errada sem iniciar sessao');

  let sent = false, message = '';
  admin.ocultarAlerta = () => {};
  admin.validarCpf = () => true;
  admin.mostrarAlerta = (_, value) => { message = value; };
  admin.apiRequest = async () => { sent = true; };
  await admin.cadastrarAluno({ cpf: 'somente-teste', altura: 170, peso: 70 });
  assert.equal(sent, false);
  assert.match(message, /metros/);
  console.log('[OK] Altura 170m e recusada antes de enviar o cadastro');

  admin.escaparTexto = value => String(value ?? '').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
  admin.identificacaoAluno = () => 'Aluno de teste';
  const container = {};
  admin.renderizarFichas([{ id: 'f', alunoId: 'a', titulo: 'Ficha', objetivo: 'Forca',
    itens: [{ id: 'i', nomeExercicio: '<script>', grupoMuscular: 'Peito', series: 3,
      repeticoes: 12, cargaSugerida: 10, cargaUsada: 12, concluido: true }] }], container, 'a');
  assert.match(container.innerHTML, /12 kg/);
  assert.match(container.innerHTML, /&lt;script&gt;/);
  assert.doesNotMatch(container.innerHTML, /checkbox|registrarExecucao|Salvar|Conclu.do/);
  console.log('[OK] Gestao consulta carga real sem controles de execucao');
})().catch(error => { console.error(error); process.exitCode = 1; });
