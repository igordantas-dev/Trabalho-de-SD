/**
 * GymFlow Pro — Lógica do Frontend SPA
 * Comunicação direta com o API Gateway via REST/JSON com Bearer JWT.
 */

class GymApp {
  constructor() {
    this.token = localStorage.getItem('gymdist_jwt_token') || null;
    this.alunos = [];
    this.consultaFichas = 0;
    this.geracaoTreino = 0;
    this.consultaMensalidades = 0;
    this.contextoAba = 0;
    this.estadosFormularios = new Map();
    this.init();
  }

  async init() {
    this.setupEventListeners();
    this.atualizarStatusJwt();

    if (this.token) {
      const resp = await this.apiRequest('/api/sessao');
      if (resp.ok) {
        const sessao = await resp.json();
        if (sessao.perfil !== 'ADMIN') { this.encerrarSessao(false); return; }
        this.usuario = sessao.usuario;
        this.atualizarStatusJwt();
        await this.carregarAlunos();
      }
    } else {
      this.abrirModal('modal-login');
    }

    this.adicionarExercicioLinha('Supino Reto', 'Peito', 4, 10, 30);
    this.adicionarExercicioLinha('Tríceps Corda', 'Tríceps', 3, 12, 15);
    document.querySelectorAll('.tab-pane form').forEach(form => this.registrarEstadoFormulario(form.id));
  }

  // ─── Autenticação & JWT ───────────────────────────────────────────────────

  atualizarStatusJwt() {
    const indicator = document.getElementById('jwt-indicator');
    const label = document.getElementById('jwt-label');
    const btnLogin = document.getElementById('btn-login-modal');

    if (this.token) {
      indicator?.classList.add('active');
      if (label) label.textContent = this.usuario ? `Gestão: ${this.usuario}` : 'Gestão autenticada';
      if (btnLogin) btnLogin.innerHTML = `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg> Sair`;
    } else {
      indicator?.classList.remove('active');
      if (label) label.textContent = 'Não autenticado';
      if (btnLogin) btnLogin.innerHTML = `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg> Entrar`;
    }
  }

  setToken(novoToken) {
    this.token = novoToken;
    if (novoToken) {
      localStorage.setItem('gymdist_jwt_token', novoToken);
    } else {
      localStorage.removeItem('gymdist_jwt_token');
    }
    this.atualizarStatusJwt();
  }

  async realizarLogin(email, senha) {
    const botao = document.getElementById('btn-do-login');
    const alerta = document.getElementById('alerta-login');
    if (botao.disabled) return;
    botao.disabled = true; alerta.classList.add('d-none');
    try {
      const resp = await GymHttp.request('/api/autenticar', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: email.trim(), senha, perfil: 'ADMIN' })
      });
      const data = await resp.json();
      if (!resp.ok) { alerta.textContent = data.mensagem; alerta.classList.remove('d-none'); return; }
      if (data.perfil !== 'ADMIN' || !data.token) throw new Error('Esta conta não pode acessar a gestão.');
      this.usuario = data.usuario;
      this.setToken(data.token);
      document.getElementById('form-login').reset();
      this.fecharModal('modal-login');
      this.mostrarToast('Login na gestão realizado com sucesso.', 'success');
      await this.carregarAlunos();
    } catch (error) {
      alerta.textContent = error.message; alerta.classList.remove('d-none');
    } finally { botao.disabled = false; }
  }

  encerrarSessao(confirmar = true) {
    const alteracoes = Array.from(document.querySelectorAll('.tab-pane')).some(aba => this.abaTemAlteracoes(aba));
    if (confirmar && alteracoes && !window.confirm('Há alterações não salvas. Deseja descartá-las e encerrar a sessão?')) return false;
    this.setToken(null);
    // Recarrega a pagina sem o token, limpando os dados e formularios em memoria.
    window.location.reload();
    return true;
  }

  // ─── Wrapper de Requisições com JWT (Bearer) ──────────────────────────────

  async apiRequest(endpoint, options = {}) {
    const headers = options.headers || {};
    headers['Content-Type'] = 'application/json';

    if (this.token && !options.skipAuth) {
      headers['Authorization'] = `Bearer ${this.token}`;
    }

    try {
      const response = await GymHttp.request(endpoint, {
        ...options,
        headers
      });

      // Se não autenticado (401), exige login
      if (response.status === 401) {
        this.encerrarSessao(false);
      }

      return response;
    } catch (err) {
      this.mostrarToast(`Falha de comunicação: ${err.message}`, 'danger');
      throw err;
    }
  }

  // ─── Módulo: Alunos ───────────────────────────────────────────────────────

  async carregarAlunos() {
    try {
      const resp = await this.apiRequest('/api/listarAlunos');
      if (resp.ok) {
        this.alunos = await resp.json();

        // Recalcula a situacao financeira nos dois sentidos, preservando matriculas inativas.
        await this.sincronizarInadimplenciaAlunos();

        this.renderizarTabelaAlunos();
        this.atualizarSelectsAlunos();
        await this.atualizarMetricasDashboard();
        this.renderizarAlunosRecentesDashboard();
      } else {
        const err = await resp.json().catch(() => ({ mensagem: 'Erro ao listar alunos.' }));
        this.tratarErrosApi(err);
      }
    } catch (e) {
      console.error('Erro ao listar alunos:', e);
    }
  }

  async sincronizarInadimplenciaAlunos() {
    const hoje = new Date().toISOString().split('T')[0];
    let houveFalha = false;
    for (const aluno of this.alunos) {
      if (!['ATIVO', 'INADIMPLENTE'].includes(aluno.status)) continue;
      try {
        const resp = await this.apiRequest(`/api/listarMensalidadesAluno/${aluno.id}`);
        // Uma falha de consulta nao significa que o aluno esta sem dividas.
        if (!resp.ok) { houveFalha = true; continue; }
        const mensalidades = await resp.json();
        const temVencida = mensalidades.some(m =>
          m.status === 'ATRASADO' || (m.status === 'PENDENTE' && m.dataVencimento && m.dataVencimento < hoje)
        );
        const novoStatus = temVencida ? 'INADIMPLENTE' : 'ATIVO';
        if (aluno.status === novoStatus) continue;
        const atualizacao = await this.apiRequest(`/api/atualizarStatusAluno/${aluno.id}`, {
          method: 'PATCH', body: JSON.stringify({ status: novoStatus })
        });
        if (atualizacao.ok) aluno.status = novoStatus;
        else houveFalha = true;
      } catch (_) { houveFalha = true; }
    }
    if (houveFalha) this.mostrarToast('Não foi possível atualizar a situação financeira de alguns alunos. Tente recarregar em instantes.', 'warning');
  }

  renderizarTabelaAlunos() {
    const tbody = document.getElementById('tbody-alunos');
    if (!tbody) return;

    if (!this.alunos || this.alunos.length === 0) {
      tbody.innerHTML = `<tr><td colspan="5" class="text-center text-muted py-4">Nenhum aluno cadastrado no momento. Preencha o formulário ao lado para cadastrar o primeiro aluno!</td></tr>`;
      return;
    }

    tbody.innerHTML = this.alunos.map(aluno => {
      const statusBadge = aluno.status === 'ATIVO'
        ? '<span class="badge badge-success">Ativo</span>'
        : aluno.status === 'INADIMPLENTE'
          ? '<span class="badge badge-warning">Inadimplente</span>'
          : '<span class="badge badge-danger">Inativo</span>';

      const biometria = (aluno.peso ? `${aluno.peso}kg` : '—') + ' / ' + (aluno.altura ? `${aluno.altura}m` : '—');

      return `
        <tr>
          <td>
            <strong>${this.escaparTexto(aluno.nome)}</strong><br>
            <small class="text-muted">${this.escaparTexto(aluno.cpf)}</small>
          </td>
          <td>${this.escaparTexto(aluno.email)}</td>
          <td>${biometria}</td>
          <td>${statusBadge}</td>
          <td>
            <div class="btn-actions-cell">
              <button class="btn btn-xs btn-action-view" onclick="app.abrirFichasDoAluno('${aluno.id}')" title="Ver treinos e fichas deste aluno">
                📋 Fichas
              </button>
              <button class="btn btn-xs btn-action-money" onclick="app.abrirMensalidadesDoAluno('${aluno.id}')" title="Ver cobranças e mensalidades deste aluno">
                💳 Mensalidades
              </button>
              <button class="btn btn-xs btn-outline" onclick="app.abrirModalSenhaAluno('${aluno.id}')">Definir senha</button>
              <button class="btn btn-xs btn-outline" onclick="app.abrirModalStatusAluno('${aluno.id}')" title="Alterar status de matrícula (Ativo, Inadimplente, Inativo)">
                🔄 Status
              </button>
            </div>
          </td>
        </tr>
      `;
    }).join('');
  }

  // Validador oficial de CPF
  validarCpf(cpf) {
    if (!cpf) return false;
    const limpo = cpf.replace(/\D/g, '');
    if (limpo.length !== 11) return false;
    if (/^(\d)\1{10}$/.test(limpo)) return false; // Bloqueia repetidos como 111.111.111-11

    let soma = 0;
    for (let i = 0; i < 9; i++) {
      soma += parseInt(limpo.charAt(i)) * (10 - i);
    }
    let resto = (soma * 10) % 11;
    if (resto === 10 || resto === 11) resto = 0;
    if (resto !== parseInt(limpo.charAt(9))) return false;

    soma = 0;
    for (let i = 0; i < 10; i++) {
      soma += parseInt(limpo.charAt(i)) * (11 - i);
    }
    resto = (soma * 10) % 11;
    if (resto === 10 || resto === 11) resto = 0;
    return resto === parseInt(limpo.charAt(10));
  }

  renderizarAlunosRecentesDashboard() {
    const container = document.getElementById('dashboard-alunos-recentes');
    if (!container) return;

    if (!this.alunos || this.alunos.length === 0) {
      container.innerHTML = `<p class="text-muted text-center py-4">Nenhum aluno cadastrado ainda.</p>`;
      return;
    }

    const recentes = this.alunos.slice(-5).reverse();
    container.innerHTML = recentes.map(a => {
      const statusBadge = a.status === 'ATIVO'
        ? '<span class="badge badge-success">Ativo</span>'
        : a.status === 'INADIMPLENTE'
          ? '<span class="badge badge-warning">Inadimplente</span>'
          : '<span class="badge badge-danger">Inativo</span>';
      return `
        <div class="status-row">
          <div>
            <strong style="font-size:0.88rem">${this.escaparTexto(a.nome)}</strong>
            <small class="text-muted d-block">${this.escaparTexto(a.email)}</small>
          </div>
          ${statusBadge}
        </div>
      `;
    }).join('');
  }

  async cadastrarAluno(dados) {
    this.ocultarAlerta('alerta-erro-aluno');

    // Validação de CPF antes do envio
    if (!this.validarCpf(dados.cpf)) {
      this.mostrarAlerta('alerta-erro-aluno', 'CPF inválido! Verifique a quantidade de dígitos e o cálculo do CPF.', null, 'warning');
      this.mostrarToast('CPF inválido. Digite um CPF válido de 11 dígitos.', 'warning');
      return;
    }

    if (dados.altura != null && (!Number.isFinite(dados.altura) || dados.altura < 0.5 || dados.altura > 2.5)) {
      this.mostrarAlerta('alerta-erro-aluno', 'Informe a altura em metros, entre 0,50 e 2,50. Exemplo: 1,70 para 170 cm.'); return;
    }
    if (dados.peso != null && (!Number.isFinite(dados.peso) || dados.peso < 1 || dados.peso > 500)) {
      this.mostrarAlerta('alerta-erro-aluno', 'Informe o peso em kg, entre 1 e 500.'); return;
    }
    const resp = await this.apiRequest('/api/cadastrarAluno', {
      method: 'POST',
      body: JSON.stringify(dados)
    });

    if (resp.status === 201) {
      const aluno = await resp.json();
      this.mostrarToast(`Aluno "${aluno.nome}" cadastrado com sucesso!`, 'success');
      document.getElementById('form-cadastrar-aluno').reset();
      this.registrarEstadoFormulario('form-cadastrar-aluno');
      await this.carregarAlunos();
    } else {
      const err = await resp.json().catch(() => ({ mensagem: 'Erro desconhecido ao cadastrar aluno' }));
      this.tratarErrosApi(err, 'alerta-erro-aluno');
    }
  }

  abrirModalSenhaAluno(id) {
    const aluno = this.alunos.find(item => item.id === id);
    if (!aluno) return;
    document.getElementById('form-senha-aluno').reset();
    document.getElementById('senha-aluno-id').value = id;
    document.getElementById('senha-aluno-nome').textContent = `Aluno: ${aluno.nome}. Login: ${aluno.email}`;
    this.ocultarAlerta('alerta-senha-aluno');
    this.abrirModal('modal-senha-aluno');
  }

  async definirSenhaAluno() {
    const senha = document.getElementById('nova-senha-aluno').value;
    if (senha !== document.getElementById('confirmar-senha-aluno').value) {
      this.mostrarAlerta('alerta-senha-aluno', 'As senhas não coincidem.'); return;
    }
    const id = document.getElementById('senha-aluno-id').value;
    const resp = await this.apiRequest(`/api/definirSenhaAluno/${id}`, { method:'POST', body:JSON.stringify({senha}) });
    if (!resp.ok) { this.tratarErrosApi(await resp.json(), 'alerta-senha-aluno'); return; }
    document.getElementById('form-senha-aluno').reset();
    this.fecharModal('modal-senha-aluno');
    this.mostrarToast('Senha do aluno atualizada. Informe a nova senha ao aluno por um canal privado.', 'success');
  }

  abrirModalStatusAluno(id) {
    const aluno = this.alunos.find(item => item.id === id);
    if (!aluno) return;
    const nome = aluno.nome, statusAtual = aluno.status;
    document.getElementById('status-aluno-id').value = id;
    document.getElementById('status-aluno-nome').textContent = `Aluno: ${nome}`;
    const select = document.getElementById('select-novo-status-aluno');
    if (select) select.value = statusAtual;
    this.abrirModal('modal-status-aluno');
  }

  async atualizarStatusAluno(id, novoStatus) {
    const resp = await this.apiRequest(`/api/atualizarStatusAluno/${id}`, {
      method: 'PATCH',
      body: JSON.stringify({ status: novoStatus })
    });

    if (resp.ok) {
      this.mostrarToast(`Status do aluno atualizado para ${novoStatus}!`, 'success');
      this.fecharModal('modal-status-aluno');
      await this.carregarAlunos();
    } else {
      const err = await resp.json().catch(() => ({ mensagem: 'Falha ao atualizar status do aluno' }));
      this.tratarErrosApi(err);
    }
  }

  async alternarStatusAluno(id, statusAtual) {
    const novoStatus = statusAtual === 'ATIVO' ? 'INATIVO' : 'ATIVO';
    await this.atualizarStatusAluno(id, novoStatus);
  }

  atualizarSelectsAlunos() {
    const selects = [
      document.getElementById('treino-aluno-select'),
      document.getElementById('select-filtro-aluno-treino'),
      document.getElementById('ia-aluno-select'),
      document.getElementById('fin-aluno-select'),
      document.getElementById('select-filtro-aluno-fin')
    ];

    selects.forEach(sel => {
      if (!sel) return;
      const valorAtual = sel.value;
      sel.innerHTML = '<option value="">-- Selecione o Aluno --</option>' +
        this.alunos.map(a => `<option value="${a.id}">${this.escaparTexto(a.nome)} (${this.escaparTexto(a.cpf)})</option>`).join('');
      if (valorAtual) sel.value = valorAtual;
    });
  }

  escaparTexto(valor) {
    const texto = document.createElement('span'); texto.textContent = String(valor ?? ''); return texto.innerHTML;
  }

  identificacaoAluno(alunoId) {
    const aluno = this.alunos.find(a => a.id === alunoId);
    const identificacao = document.createElement('small');
    identificacao.className = 'text-muted d-block';
    identificacao.textContent = `Aluno: ${aluno ? aluno.nome : alunoId}`;
    return identificacao.outerHTML;
  }

  limparResultadoIa() {
    this.geracaoTreino++;
    const container = document.getElementById('ia-resultado-container');
    if (container) {
      container.textContent = 'Selecione um aluno e gere um treino para visualizar o resultado.';
    }
  }

  abrirCadastroAluno() {
    if (!this.navegarParaAba('tab-alunos')) return;
    document.getElementById('painel-cadastrar-aluno').open = true;
    document.getElementById('aluno-nome').focus();
  }

  abrirFichasDoAluno(alunoId) {
    if (!this.navegarParaAba('tab-treinos')) return;
    document.getElementById('painel-fichas-aluno').open = true;
    const selectFiltro = document.getElementById('select-filtro-aluno-treino');
    if (selectFiltro) selectFiltro.value = alunoId;
    const selectCriar = document.getElementById('treino-aluno-select');
    if (selectCriar) selectCriar.value = alunoId;
    this.carregarFichasDoAluno(alunoId);
  }

  abrirMensalidadesDoAluno(alunoId) {
    if (!this.navegarParaAba('tab-financeiro')) return;
    document.getElementById('painel-mensalidades-aluno').open = true;
    const selectFiltro = document.getElementById('select-filtro-aluno-fin');
    if (selectFiltro) selectFiltro.value = alunoId;
    const selectGerar = document.getElementById('fin-aluno-select');
    if (selectGerar) selectGerar.value = alunoId;
    this.carregarMensalidadesDoAluno(alunoId);
  }

  // ─── Módulo: Fichas de Treino & Execução ───────────────────────────────────

  adicionarExercicioLinha(nome = '', grupo = '', series = 3, reps = 12, carga = 0) {
    const container = document.getElementById('exercicios-container');
    const id = 'ex_' + Math.random().toString(36).substring(2, 9);

    const div = document.createElement('div');
    div.className = 'exercicio-row';
    div.id = id;
    const campos = [
      { campo: 'nome', rotulo: 'Exercício', tipo: 'text', valor: nome, placeholder: 'Ex.: Supino', obrigatorio: true },
      { campo: 'grupo', rotulo: 'Grupo muscular', tipo: 'text', valor: grupo, placeholder: 'Ex.: Peito' },
      { campo: 'series', rotulo: 'Séries', tipo: 'number', valor: series, minimo: 1, obrigatorio: true },
      { campo: 'reps', rotulo: 'Repetições', tipo: 'number', valor: reps, minimo: 1, obrigatorio: true },
      { campo: 'carga', rotulo: 'Carga sugerida (kg)', tipo: 'number', valor: carga, minimo: 0, passo: '0.5' }
    ];

    campos.forEach(campo => {
      const label = document.createElement('label');
      label.className = 'exercicio-campo';
      const texto = document.createElement('span');
      texto.textContent = campo.rotulo;
      const input = document.createElement('input');
      input.id = `${id}_${campo.campo}`;
      label.htmlFor = input.id;
      input.type = campo.tipo;
      input.className = 'form-control';
      input.dataset.field = campo.campo;
      input.value = campo.valor;
      input.required = Boolean(campo.obrigatorio);
      if (campo.placeholder) input.placeholder = campo.placeholder;
      if (campo.minimo !== undefined) input.min = campo.minimo;
      if (campo.passo) input.step = campo.passo;
      label.append(texto, input);
      div.appendChild(label);
    });

    const remover = document.createElement('button');
    remover.type = 'button';
    remover.className = 'btn btn-xs btn-danger btn-remove-row';
    remover.textContent = '×';
    remover.title = 'Remover exercício';
    remover.setAttribute('aria-label', 'Remover exercício');
    remover.addEventListener('click', () => div.remove());
    div.appendChild(remover);
    container.appendChild(div);
  }

  async criarFichaTreino(dados) {
    this.ocultarAlerta('alerta-erro-ficha');
    if (!dados.alunoId) {
      this.mostrarAlerta('alerta-erro-ficha', 'Selecione um aluno para vincular a ficha de treino.');
      this.mostrarToast('Selecione um aluno para vincular a ficha.', 'warning');
      return;
    }

    const contexto = this.contextoAba;
    const resp = await this.apiRequest('/api/criarFichaTreino', {
      method: 'POST',
      body: JSON.stringify(dados)
    });

    if (resp.status === 201) {
      const ficha = await resp.json();
      this.mostrarToast(`Ficha "${ficha.titulo}" criada com sucesso!`, 'success');
      if (contexto !== this.contextoAba) return;
      if (document.getElementById('treino-aluno-select').value !== dados.alunoId) return;
      document.getElementById('form-criar-ficha').reset();
      document.getElementById('treino-aluno-select').value = dados.alunoId;
      document.getElementById('exercicios-container').innerHTML = '';
      this.adicionarExercicioLinha();
      this.registrarEstadoFormulario('form-criar-ficha');

      // Seleciona o aluno no filtro lateral e carrega as fichas automaticamente
      const selectFiltro = document.getElementById('select-filtro-aluno-treino');
      if (selectFiltro) selectFiltro.value = dados.alunoId;
      await this.carregarFichasDoAluno(dados.alunoId);
    } else {
      const err = await resp.json().catch(() => ({ mensagem: 'Falha ao criar ficha de treino' }));
      if (contexto !== this.contextoAba) return;
      this.tratarErrosApi(err, 'alerta-erro-ficha');
    }
  }

  async carregarFichasDoAluno(alunoId) {
    const container = document.getElementById('fichas-container');
    if (!container) return;
    const consulta = ++this.consultaFichas;
    for (const id of ['treino-aluno-select', 'select-filtro-aluno-treino']) {
      const select = document.getElementById(id);
      if (select) select.value = alunoId;
    }

    if (!alunoId) {
      container.innerHTML = '<p class="text-muted text-center py-4">Selecione um aluno acima para ver suas fichas e registrar execuções.</p>';
      return;
    }

    container.innerHTML = '<p class="text-muted text-center py-4">⏳ Carregando fichas do aluno...</p>';

    try {
      const resp = await this.apiRequest(`/api/listarFichasAluno/${alunoId}`);
      if (consulta !== this.consultaFichas) return;
      if (resp.ok) {
        const fichas = await resp.json();
        if (consulta !== this.consultaFichas) return;
        this.renderizarFichas(fichas, container, alunoId);
      } else {
        const err = await resp.json().catch(() => ({ mensagem: 'Falha ao buscar fichas de treino.' }));
        if (consulta !== this.consultaFichas) return;
        this.tratarErrosApi(err);
        container.innerHTML = `
          <div class="alert alert-danger mt-3">
            <span class="alert-icon">⚠️</span>
            <div class="alert-content">
              <strong>Não foi possível carregar as fichas</strong>
              <p>${this.escaparTexto(err.mensagem || 'Não foi possível carregar as fichas. Tente novamente.')}</p>
              <div class="mt-2">
                <button class="btn btn-xs btn-outline" onclick="app.carregarFichasDoAluno('${alunoId}')">Tentar Novamente</button>
              </div>
            </div>
          </div>
        `;
      }
    } catch (e) {
      if (consulta !== this.consultaFichas) return;
      container.innerHTML = `
        <div class="alert alert-danger mt-3">
          <span class="alert-icon">⚠️</span>
          <div class="alert-content">
            <strong>Falha de conexão</strong>
            <p>Não foi possível conectar ao API Gateway: ${e.message}</p>
            <div class="mt-2">
              <button class="btn btn-xs btn-outline" onclick="app.carregarFichasDoAluno('${alunoId}')">Tentar Novamente</button>
            </div>
          </div>
        </div>
      `;
    }
  }

  renderizarFichas(fichas, container, alunoId) {
    if (!fichas || fichas.length === 0) {
      container.innerHTML = `
        <div class="text-center py-5">
          <p class="text-muted mb-2">Nenhuma ficha encontrada para este aluno.</p>
          <small class="text-muted d-block mb-3">Crie uma ficha personalizada no formulário ao lado ou use o Assistente IA ✨</small>
          <button class="btn btn-xs btn-primary" onclick="app.navegarParaAba('tab-ia')">Ir para Gerador IA ✨</button>
        </div>
      `;
      return;
    }

    container.innerHTML = fichas.map(f => {
      const itensHtml = f.itens.map(item => `
        <div class="exercicio-item-card ficha-consulta" data-item-id="${item.id}">
          <div>
            <strong>${this.escaparTexto(item.nomeExercicio)}</strong><br>
            <small class="text-muted">${this.escaparTexto(item.grupoMuscular || 'Geral')}</small>
          </div>
          <div>${item.series}x${item.repeticoes}</div>
          <div title="Carga sugerida">Sug.: ${item.cargaSugerida}kg</div>
          <div>${item.concluido || item.cargaUsada > 0 ? item.cargaUsada + ' kg' : 'Não informada'}</div>
        </div>
      `).join('');

      return `
        <div class="ficha-card" id="card-ficha-${f.id}">
          <div class="ficha-header">
            <div>
              <strong>${this.escaparTexto(f.titulo)}</strong>
              ${this.identificacaoAluno(f.alunoId || alunoId)}
              <small class="text-muted d-block">Objetivo: ${this.escaparTexto(f.objetivo)} · ${f.dataCriacao || 'Hoje'}</small>
            </div>
          </div>
          <div class="exercicios-list mb-3">
            <div class="text-xs text-muted mb-1" style="display:grid; grid-template-columns: 2fr 1fr 1fr 1fr; padding: 0 0.85rem;">
              <span>Exercício</span><span>Séries</span><span>Sugestão</span><span>Carga informada</span>
            </div>
            ${itensHtml}
          </div>
          <small class="text-muted">Consulta da ficha prescrita e das cargas informadas pelo aluno. A execução é registrada no Portal do Aluno.</small>
        </div>
      `;
    }).join('');
  }

  // ─── Módulo: Assistente IA ✨ ──────────────────────────────────────────────

  async gerarTreinoComIa(dados) {
    this.ocultarAlerta('alerta-erro-ia');
    if (!dados.alunoId) {
      this.mostrarAlerta('alerta-erro-ia', 'Selecione um aluno para gerar o treino.');
      this.mostrarToast('Selecione um aluno para gerar o treino.', 'warning');
      return;
    }

    const btn = document.getElementById('btn-submit-ia');
    const container = document.getElementById('ia-resultado-container');
    const geracao = ++this.geracaoTreino;
    btn.disabled = true;
    btn.textContent = 'Gerando treino com IA...';
    container.innerHTML = '<p class="text-center py-5">⏳ Consultando biometria e sintetizando plano de treino personalizado...</p>';

    try {
      const resp = await this.apiRequest('/api/gerarTreinoIa', {
        method: 'POST',
        body: JSON.stringify(dados)
      });

      if (resp.ok) {
        const ficha = await resp.json();
        this.mostrarToast('Treino gerado pela IA e salvo no banco de dados!', 'success');
        if (geracao !== this.geracaoTreino) return;
        this.renderizarResultadoIa(ficha, container, dados.alunoId);
        this.registrarEstadoFormulario('form-gerar-ia');
      } else {
        const err = await resp.json().catch(() => ({ mensagem: 'Falha na geração com IA' }));
        if (geracao !== this.geracaoTreino) return;
        this.tratarErrosApi(err, 'alerta-erro-ia');
        container.innerHTML = `<div class="alert alert-danger mt-3">⚠️ ${this.escaparTexto(err.mensagem || 'Não foi possível gerar o treino. Tente novamente.')}</div>`;
      }
    } catch (e) {
      if (geracao !== this.geracaoTreino) return;
      container.innerHTML = `<div class="alert alert-danger mt-3">❌ Erro de comunicação: ${e.message}</div>`;
    } finally {
      btn.disabled = false;
      btn.textContent = 'Gerar Treino com IA ✨';
    }
  }

  renderizarResultadoIa(ficha, container, alunoId) {
    const itensHtml = ficha.itens.map(i => `
      <div class="exercicio-item-card" style="margin-bottom:0.4rem;">
        <div><strong>${this.escaparTexto(i.nomeExercicio)}</strong> <small class="text-muted">(${this.escaparTexto(i.grupoMuscular || 'Geral')})</small></div>
        <div>${i.series} séries x ${i.repeticoes} reps</div>
        <div><strong>${i.cargaSugerida} kg</strong> (sugerido)</div>
      </div>
    `).join('');

    container.innerHTML = `
      <div class="ficha-card" style="border-color: rgba(99, 102, 241, 0.4);">
        <div class="ficha-header">
          <div>
            <strong style="color:var(--accent-glow);">${this.escaparTexto(ficha.titulo)}</strong>
            ${this.identificacaoAluno(ficha.alunoId || alunoId)}
            <small class="text-muted d-block">Objetivo: ${this.escaparTexto(ficha.objetivo)} · Gerado com IA</small>
          </div>
          <span class="badge badge-success">Salvo no Banco</span>
        </div>
        <div class="exercicios-list mb-3">
          ${itensHtml}
        </div>
        <div class="card-footer-actions flex-between">
          <small class="text-muted">Esta ficha já está salva e disponível na aba Treinos.</small>
          <button class="btn btn-sm btn-primary" onclick="app.abrirFichasDoAluno('${alunoId}')">
            Ver na Aba de Fichas →
          </button>
        </div>
      </div>
    `;
  }

  // ─── Módulo: Financeiro ───────────────────────────────────────────────────

  async gerarMensalidade(dados) {
    this.ocultarAlerta('alerta-erro-mensalidade');
    if (!dados.alunoId) {
      this.mostrarAlerta('alerta-erro-mensalidade', 'Selecione um aluno para gerar a mensalidade.');
      this.mostrarToast('Selecione um aluno para gerar a mensalidade.', 'warning');
      return;
    }

    const contexto = this.contextoAba;
    const resp = await this.apiRequest('/api/gerarMensalidade', {
      method: 'POST',
      body: JSON.stringify(dados)
    });

    if (resp.status === 201) {
      const mensalidade = await resp.json();
      this.mostrarToast(`Mensalidade de R$ ${mensalidade.valor.toFixed(2)} gerada com sucesso!`, 'success');
      if (contexto !== this.contextoAba) return;
      document.getElementById('form-gerar-mensalidade').reset();
      this.registrarEstadoFormulario('form-gerar-mensalidade');
      const selectFiltro = document.getElementById('select-filtro-aluno-fin');
      if (selectFiltro) selectFiltro.value = dados.alunoId;
      await this.carregarMensalidadesDoAluno(dados.alunoId);
      await this.carregarAlunos();
    } else {
      const err = await resp.json().catch(() => ({ mensagem: 'Falha ao gerar mensalidade' }));
      if (contexto !== this.contextoAba) return;
      this.tratarErrosApi(err, 'alerta-erro-mensalidade');
    }
  }

  async carregarMensalidadesDoAluno(alunoId) {
    const tbody = document.getElementById('tbody-mensalidades');
    if (!tbody) return;
    const consulta = ++this.consultaMensalidades;

    if (!alunoId) {
      tbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted py-4">Selecione um aluno acima para listar cobranças.</td></tr>';
      return;
    }

    tbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted py-4">⏳ Carregando mensalidades do aluno...</td></tr>';

    try {
      const resp = await this.apiRequest(`/api/listarMensalidadesAluno/${alunoId}`);
      if (consulta !== this.consultaMensalidades) return;
      if (resp.ok) {
        const lista = await resp.json();
        if (consulta !== this.consultaMensalidades) return;
        this.renderizarMensalidades(lista, tbody, alunoId);
      } else {
        const err = await resp.json().catch(() => ({ mensagem: 'Falha ao buscar mensalidades.' }));
        if (consulta !== this.consultaMensalidades) return;
        this.tratarErrosApi(err);
        tbody.innerHTML = `
          <tr>
            <td colspan="5" class="text-center py-4">
              <div class="alert alert-danger m-0 d-inline-flex">
                <span class="alert-icon">⚠️</span>
                <div class="alert-content text-left">
                  <strong>Erro ao carregar mensalidades</strong>
                  <p class="m-0">${err.mensagem || 'Falha na resposta do servidor'}</p>
                  <button class="btn btn-xs btn-outline mt-2" onclick="app.carregarMensalidadesDoAluno('${alunoId}')">Tentar Novamente</button>
                </div>
              </div>
            </td>
          </tr>
        `;
      }
    } catch (e) {
      if (consulta !== this.consultaMensalidades) return;
      tbody.innerHTML = `
        <tr>
          <td colspan="5" class="text-center py-4 text-danger">
            ⚠️ Erro de conexão: ${e.message}
            <br><button class="btn btn-xs btn-outline mt-2" onclick="app.carregarMensalidadesDoAluno('${alunoId}')">Tentar Novamente</button>
          </td>
        </tr>
      `;
    }
  }

  renderizarMensalidades(mensalidades, tbody, alunoId) {
    if (!mensalidades || mensalidades.length === 0) {
      tbody.innerHTML = `
        <tr>
          <td colspan="5" class="text-center py-5">
            <p class="text-muted mb-2">Nenhuma cobrança registrada para este aluno.</p>
            <small class="text-muted">Utilize o formulário ao lado para emitir uma nova mensalidade.</small>
          </td>
        </tr>
      `;
      return;
    }

    tbody.innerHTML = mensalidades.map(m => {
      const statusBadge = m.status === 'PAGO'
        ? '<span class="badge badge-success">Pago</span>'
        : m.status === 'ATRASADO'
          ? '<span class="badge badge-danger">Atrasado</span>'
          : '<span class="badge badge-warning">Pendente</span>';

      const botaoAcao = m.status !== 'PAGO'
        ? `<button class="btn btn-xs btn-primary" onclick="app.abrirModalPagamento('${m.id}', ${m.valor})">Registrar pagamento recebido</button>`
        : `<small class="text-muted">via ${m.formaPagamento || 'PIX'}</small>`;

      return `
        <tr>
          <td>${this.escaparTexto(m.descricao || 'Mensalidade')}</td>
          <td><strong>R$ ${m.valor.toFixed(2)}</strong></td>
          <td>${m.dataVencimento}</td>
          <td>${statusBadge}</td>
          <td>${botaoAcao}</td>
        </tr>
      `;
    }).join('');
  }

  abrirModalPagamento(id, valor) {
    document.getElementById('pagamento-mensalidade-id').value = id;
    document.getElementById('pagamento-detalhes').textContent = `Valor recebido: R$ ${valor.toFixed(2)}`;
    this.abrirModal('modal-pagamento');
  }

  async confirmarPagamento(id, formaPagamento) {
    const resp = await this.apiRequest(`/api/registrarPagamentoMensalidade/${id}`, {
      method: 'POST',
      body: JSON.stringify({ formaPagamento })
    });

    if (resp.ok) {
      this.mostrarToast(`Recebimento registrado (${formaPagamento}). Mensalidade marcada como paga.`, 'success');
      this.fecharModal('modal-pagamento');
      const alunoId = document.getElementById('select-filtro-aluno-fin').value;
      if (alunoId) await this.carregarMensalidadesDoAluno(alunoId);
      await this.carregarAlunos();
    } else {
      const err = await resp.json().catch(() => ({ mensagem: 'Falha ao registrar pagamento' }));
      this.tratarErrosApi(err);
    }
  }

  // ─── Dashboard ────────────────────────────────────────────────────────────

  async atualizarMetricasDashboard() {
    const total = this.alunos.length;
    const ativos = this.alunos.filter(a => a.status === 'ATIVO').length;
    const inadimplentes = this.alunos.filter(a => a.status === 'INADIMPLENTE').length;

    // Elementos nos cards de métricas
    const elTotal = document.getElementById('dash-total-alunos') || document.getElementById('metric-total-alunos');
    const elAtivos = document.getElementById('dash-alunos-ativos') || document.getElementById('metric-alunos-ativos');
    const elInadimp = document.getElementById('dash-inadimplentes') || document.getElementById('metric-inadimplentes');
    const elTreinos = document.getElementById('dash-total-treinos');

    if (elTotal) elTotal.textContent = total;
    if (elAtivos) elAtivos.textContent = ativos;
    if (elInadimp) elInadimp.textContent = inadimplentes;

    // Contagem de fichas de treino
    if (elTreinos && this.alunos.length > 0) {
      try {
        let totalFichas = 0;
        const promises = this.alunos.map(a => 
          this.apiRequest(`/api/listarFichasAluno/${a.id}`)
            .then(r => r.ok ? r.json() : [])
            .catch(() => [])
        );
        const resultados = await Promise.all(promises);
        resultados.forEach(lista => {
          if (Array.isArray(lista)) totalFichas += lista.length;
        });
        elTreinos.textContent = totalFichas;
      } catch (e) {
        elTreinos.textContent = '—';
      }
    } else if (elTreinos) {
      elTreinos.textContent = '0';
    }
  }

  // ─── UI Helpers ───────────────────────────────────────────────────────────

  estadoFormulario(form) {
    return JSON.stringify(Array.from(form.querySelectorAll('input, select, textarea'))
      .filter(campo => !['treino-aluno-select', 'ia-aluno-select', 'fin-aluno-select'].includes(campo.id))
      .map(campo => [campo.type, campo.type === 'checkbox' ? campo.checked : campo.value]));
  }

  registrarEstadoFormulario(formId) {
    const form = document.getElementById(formId);
    if (form) this.estadosFormularios.set(formId, this.estadoFormulario(form));
  }

  abaTemAlteracoes(aba) {
    const formularioAlterado = Array.from(aba.querySelectorAll('form')).some(form =>
      this.estadosFormularios.has(form.id) && this.estadosFormularios.get(form.id) !== this.estadoFormulario(form));
    const execucaoAlterada = Array.from(aba.querySelectorAll('.input-carga-real, .chk-concluido')).some(campo =>
      campo.type === 'checkbox' ? campo.checked !== campo.defaultChecked : campo.value !== campo.defaultValue);
    return formularioAlterado || execucaoAlterada;
  }

  limparContextoAba(aba) {
    this.contextoAba++;
    aba.querySelectorAll('form').forEach(form => form.reset());
    aba.querySelectorAll('select[id$="aluno-select"], select[id^="select-filtro-aluno-"]').forEach(select => {
      select.value = '';
    });
    if (aba.id === 'tab-treinos') {
      this.carregarFichasDoAluno('');
      document.getElementById('exercicios-container').replaceChildren();
      this.adicionarExercicioLinha();
      document.getElementById('painel-nova-ficha').open = false;
      document.getElementById('painel-fichas-aluno').open = true;
    } else if (aba.id === 'tab-alunos') {
      document.getElementById('painel-cadastrar-aluno').open = false;
      document.getElementById('painel-lista-alunos').open = true;
    } else if (aba.id === 'tab-ia') {
      this.limparResultadoIa();
    } else if (aba.id === 'tab-financeiro') {
      this.carregarMensalidadesDoAluno('');
      document.getElementById('painel-gerar-mensalidade').open = false;
      document.getElementById('painel-mensalidades-aluno').open = true;
    }
    aba.querySelectorAll('[id^="alerta-erro-"]').forEach(alerta => this.ocultarAlerta(alerta.id));
    aba.querySelectorAll('form').forEach(form => this.registrarEstadoFormulario(form.id));
  }

  navegarParaAba(tabId) {
    if (!document.getElementById(tabId)?.classList.contains('tab-pane')) return false;
    const atual = document.querySelector('.tab-pane.active');
    if (atual?.id === tabId) return true;
    if (atual) {
      if (this.abaTemAlteracoes(atual) && !window.confirm('Há alterações não salvas nesta aba. Deseja descartá-las e mudar de aba?')) {
        return false;
      }
      this.limparContextoAba(atual);
    }
    document.querySelectorAll('.nav-tab').forEach(t => {
      t.classList.toggle('active', t.dataset.tab === tabId);
    });
    document.querySelectorAll('.tab-pane').forEach(p => {
      p.classList.toggle('active', p.id === tabId);
    });
    return true;
  }

  abrirModal(modalId) {
    document.getElementById(modalId)?.classList.add('open');
  }

  fecharModal(modalId) {
    document.getElementById(modalId)?.classList.remove('open');
  }

  mostrarToast(mensagem, tipo = 'info', duracao = 6000) {
    const container = document.getElementById('toast-container');
    if (!container) return;
    const toast = document.createElement('div');
    toast.className = `toast ${tipo}`;
    toast.textContent = mensagem;

    container.appendChild(toast);
    setTimeout(() => {
      toast.style.opacity = '0';
      setTimeout(() => toast.remove(), 300);
    }, duracao);
  }

  mostrarAlerta(elementId, mensagem, listaErros = null, tipo = 'danger') {
    const el = document.getElementById(elementId);
    if (!el) return;
    el.className = `alert alert-${tipo}`;

    let html = `<span class="alert-icon">${tipo === 'success' ? '✅' : tipo === 'warning' ? '⚠️' : '❌'}</span>`;
    html += `<div class="alert-content">`;
    html += `<strong>${this.escaparTexto(mensagem)}</strong>`;

    if (listaErros && Object.keys(listaErros).length > 0) {
      html += '<ul>';
      for (const [campo, desc] of Object.entries(listaErros)) {
        html += `<li><strong>${this.escaparTexto(campo)}:</strong> ${this.escaparTexto(desc)}</li>`;
      }
      html += '</ul>';
    }

    html += `</div>`;
    el.innerHTML = html;
    el.classList.remove('d-none');
    el.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  }

  ocultarAlerta(elementId) {
    const el = document.getElementById(elementId);
    if (el) {
      el.classList.add('d-none');
      el.innerHTML = '';
    }
  }

  tratarErrosApi(err, containerAlertaId = null) {
    let tituloToast = err.mensagem || 'Ocorreu um erro na operação.';
    if (err.erros && Object.keys(err.erros).length > 0) {
      const campos = Object.entries(err.erros).map(([c, m]) => `${c}: ${m}`).join(' | ');
      tituloToast = `Dados inválidos: ${campos}`;
      if (containerAlertaId) {
        this.mostrarAlerta(containerAlertaId, err.mensagem || 'Corrija os erros abaixo para prosseguir:', err.erros, 'danger');
      }
      this.mostrarToast(tituloToast, 'warning', 7000);
    } else {
      if (containerAlertaId) {
        this.mostrarAlerta(containerAlertaId, err.mensagem || 'Falha ao processar a requisição.', null, 'danger');
      }
      this.mostrarToast(tituloToast, 'danger', 6000);
    }
  }

  setupEventListeners() {
    // Abas
    document.querySelectorAll('.nav-tab').forEach(tab => {
      tab.addEventListener('click', () => this.navegarParaAba(tab.dataset.tab));
    });

    // Login da gestao e saida direta.
    document.getElementById('btn-login-modal')?.addEventListener('click', () => {
      if (this.token) this.encerrarSessao();
      else this.abrirModal('modal-login');
    });
    document.getElementById('form-login')?.addEventListener('submit', event => {
      event.preventDefault();
      this.realizarLogin(document.getElementById('login-email').value, document.getElementById('login-senha').value);
    });

    document.getElementById('form-senha-aluno')?.addEventListener('submit', event => {
      event.preventDefault(); this.definirSenhaAluno();
    });
    window.addEventListener('unhandledrejection', event => {
      event.preventDefault();
      this.mostrarToast('Não foi possível concluir a operação. Tente novamente em instantes.', 'danger');
    });

    // Form Aluno
    document.getElementById('form-cadastrar-aluno')?.addEventListener('submit', (e) => {
      e.preventDefault();
      const dados = {
        nome: document.getElementById('aluno-nome').value.trim(),
        cpf: document.getElementById('aluno-cpf').value.trim(),
        email: document.getElementById('aluno-email').value.trim(),
        telefone: document.getElementById('aluno-telefone').value.trim() || null,
        peso: document.getElementById('aluno-peso').value === '' ? null : Number(document.getElementById('aluno-peso').value),
        altura: document.getElementById('aluno-altura').value === '' ? null : Number(document.getElementById('aluno-altura').value),
        senha: document.getElementById('aluno-senha').value
      };
      this.cadastrarAluno(dados);
    });

    document.getElementById('btn-refresh-alunos')?.addEventListener('click', () => {
      this.carregarAlunos();
      this.mostrarToast('Lista de alunos atualizada.', 'info');
    });

    // Form Ficha Manual
    document.getElementById('btn-add-exercicio')?.addEventListener('click', () => {
      this.adicionarExercicioLinha();
    });

    document.getElementById('form-criar-ficha')?.addEventListener('submit', (e) => {
      e.preventDefault();
      const alunoId = document.getElementById('treino-aluno-select').value;
      const titulo = document.getElementById('treino-titulo').value.trim();
      const objetivo = document.getElementById('treino-objetivo').value.trim();

      const itens = [];
      document.querySelectorAll('#exercicios-container .exercicio-row').forEach(row => {
        itens.push({
          nomeExercicio: row.querySelector('[data-field="nome"]').value.trim(),
          grupoMuscular: row.querySelector('[data-field="grupo"]').value.trim(),
          series: parseInt(row.querySelector('[data-field="series"]').value) || 3,
          repeticoes: parseInt(row.querySelector('[data-field="reps"]').value) || 10,
          cargaSugerida: parseFloat(row.querySelector('[data-field="carga"]').value) || 0
        });
      });

      this.criarFichaTreino({ alunoId, titulo, objetivo, itens });
    });

    document.getElementById('treino-aluno-select')?.addEventListener('change', (e) => {
      this.carregarFichasDoAluno(e.target.value);
    });

    document.getElementById('select-filtro-aluno-treino')?.addEventListener('change', (e) => {
      this.carregarFichasDoAluno(e.target.value);
    });

    document.getElementById('btn-refresh-fichas')?.addEventListener('click', () => {
      const alunoId = document.getElementById('select-filtro-aluno-treino')?.value;
      if (alunoId) {
        this.carregarFichasDoAluno(alunoId);
        this.mostrarToast('Fichas de treino atualizadas.', 'info');
      } else {
        this.mostrarToast('Selecione um aluno para recarregar suas fichas.', 'warning');
      }
    });

    document.getElementById('ia-aluno-select')?.addEventListener('change', () => {
      this.limparResultadoIa();
    });

    // Form Gerador IA
    document.getElementById('form-gerar-ia')?.addEventListener('submit', (e) => {
      e.preventDefault();
      const dados = {
        alunoId: document.getElementById('ia-aluno-select').value,
        objetivo: document.getElementById('ia-objetivo').value,
        nivel: document.getElementById('ia-nivel').value,
        focoMuscular: document.getElementById('ia-foco').value.trim() || null,
        observacoes: document.getElementById('ia-observacoes').value.trim() || null
      };
      this.gerarTreinoComIa(dados);
    });

    // Form Mensalidade
    document.getElementById('form-gerar-mensalidade')?.addEventListener('submit', (e) => {
      e.preventDefault();
      const dados = {
        alunoId: document.getElementById('fin-aluno-select').value,
        valor: parseFloat(document.getElementById('fin-valor').value) || 0,
        dataVencimento: document.getElementById('fin-vencimento').value,
        descricao: document.getElementById('fin-descricao').value.trim() || null
      };
      this.gerarMensalidade(dados);
    });

    document.getElementById('select-filtro-aluno-fin')?.addEventListener('change', (e) => {
      this.carregarMensalidadesDoAluno(e.target.value);
    });

    document.getElementById('btn-refresh-fin')?.addEventListener('click', () => {
      const alunoId = document.getElementById('select-filtro-aluno-fin')?.value;
      if (alunoId) {
        this.carregarMensalidadesDoAluno(alunoId);
        this.mostrarToast('Mensalidades atualizadas.', 'info');
      } else {
        this.mostrarToast('Selecione um aluno para recarregar suas mensalidades.', 'warning');
      }
    });

    // Form Pagamento
    document.getElementById('form-quitar-mensalidade')?.addEventListener('submit', (e) => {
      e.preventDefault();
      const id = document.getElementById('pagamento-mensalidade-id').value;
      const forma = document.getElementById('pagamento-forma').value;
      this.confirmarPagamento(id, forma);
    });

    // Form Alterar Status Aluno
    document.getElementById('form-alterar-status-aluno')?.addEventListener('submit', (e) => {
      e.preventDefault();
      const id = document.getElementById('status-aluno-id').value;
      const novoStatus = document.getElementById('select-novo-status-aluno').value;
      if (id && novoStatus) {
        this.atualizarStatusAluno(id, novoStatus);
      }
    });

    // Preenche data de vencimento padrão para daqui 30 dias
    const hoje = new Date();
    hoje.setDate(hoje.getDate() + 30);
    const dataVenc = hoje.toISOString().split('T')[0];
    const elVenc = document.getElementById('fin-vencimento');
    if (elVenc) elVenc.value = dataVenc;
  }
}

// Inicializa a aplicação
const app = new GymApp();
