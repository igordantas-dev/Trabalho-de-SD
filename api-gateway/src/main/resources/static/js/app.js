/**
 * GymFlow Pro — Lógica do Frontend SPA
 * Comunicação direta com o API Gateway via REST/JSON com Bearer JWT.
 */

class GymApp {
  constructor() {
    this.token = localStorage.getItem('gymdist_jwt_token') || null;
    this.alunos = [];
    this.init();
  }

  async init() {
    this.setupEventListeners();
    this.atualizarStatusJwt();

    // Se houver token salvo, carrega dados
    if (this.token) {
      await this.carregarAlunos();
    } else {
      // Abre modal de login ou informa usuário
      this.atualizarStatusJwt();
      this.abrirModal('modal-login');
      this.mostrarToast('Por favor, efetue login para gerenciar o sistema.', 'info');
    }

    this.adicionarExercicioLinha('Supino Reto', 'Peito', 4, 10, 30);
    this.adicionarExercicioLinha('Tríceps Corda', 'Tríceps', 3, 12, 15);
  }

  // ─── Autenticação & JWT ───────────────────────────────────────────────────

  atualizarStatusJwt() {
    const indicator = document.getElementById('jwt-indicator');
    const label = document.getElementById('jwt-label');
    const textarea = document.getElementById('token-textarea');
    const btnLogin = document.getElementById('btn-login-modal');

    if (this.token) {
      indicator?.classList.add('active');
      if (label) label.textContent = 'Admin Autenticado';
      if (textarea) textarea.value = this.token;
      if (btnLogin) btnLogin.innerHTML = `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg> Sair`;
    } else {
      indicator?.classList.remove('active');
      if (label) label.textContent = 'Não Autenticado';
      if (textarea) textarea.value = '';
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

  async obterTokenRapido() {
    try {
      const resp = await fetch('/api/gerarToken');
      if (resp.ok) {
        const data = await resp.json();
        this.setToken(data.token);
        this.mostrarToast('Sessão iniciada como Administrador!', 'success');
        await this.carregarAlunos();
      }
    } catch (e) {
      console.warn('Erro ao obter token:', e);
    }
  }

  async realizarLogin(username, senha) {
    try {
      const resp = await fetch('/api/autenticar', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, senha })
      });

      if (resp.ok) {
        const data = await resp.json();
        this.setToken(data.token);
        this.mostrarToast(`Bem-vindo, ${data.usuario}!`, 'success');
        this.fecharModal('modal-login');
        await this.carregarAlunos();
      } else {
        const err = await resp.json().catch(() => ({ mensagem: 'Credenciais inválidas' }));
        this.mostrarToast(`Falha no login: ${err.mensagem || 'Credenciais inválidas'}`, 'danger');
      }
    } catch (e) {
      this.mostrarToast(`Erro de conexão: ${e.message}`, 'danger');
    }
  }

  // ─── Wrapper de Requisições com JWT (Bearer) ──────────────────────────────

  async apiRequest(endpoint, options = {}) {
    const headers = options.headers || {};
    headers['Content-Type'] = 'application/json';

    if (this.token && !options.skipAuth) {
      headers['Authorization'] = `Bearer ${this.token}`;
    }

    try {
      const response = await fetch(endpoint, {
        ...options,
        headers
      });

      // Se não autenticado (401), exige login
      if (response.status === 401) {
        this.setToken(null);
        this.mostrarToast('Sessão expirada ou não autorizada. Faça login.', 'warning');
        this.abrirModal('modal-login');
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

        // Verificação inteligente de inadimplência:
        // Se o aluno estiver ATIVO mas tiver qualquer mensalidade ATRASADA, atualiza status para INADIMPLENTE
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
    for (const aluno of this.alunos) {
      if (aluno.status === 'ATIVO') {
        try {
          const resp = await this.apiRequest(`/api/listarMensalidadesAluno/${aluno.id}`);
          if (resp.ok) {
            const mensalidades = await resp.json();
            const temVencida = mensalidades.some(m => 
              m.status === 'ATRASADO' || (m.status === 'PENDENTE' && m.dataVencimento && m.dataVencimento < hoje)
            );
            if (temVencida) {
              await this.apiRequest(`/api/atualizarStatusAluno/${aluno.id}`, {
                method: 'PATCH',
                body: JSON.stringify({ status: 'INADIMPLENTE' })
              });
              aluno.status = 'INADIMPLENTE';
            }
          }
        } catch (_) {}
      }
    }
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
            <strong>${aluno.nome}</strong><br>
            <small class="text-muted">${aluno.cpf}</small>
          </td>
          <td>${aluno.email}</td>
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
              <button class="btn btn-xs btn-outline" onclick="app.abrirModalStatusAluno('${aluno.id}', '${aluno.nome}', '${aluno.status}')" title="Alterar status de matrícula (Ativo, Inadimplente, Inativo)">
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
            <strong style="font-size:0.88rem">${a.nome}</strong>
            <small class="text-muted d-block">${a.email}</small>
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

    const resp = await this.apiRequest('/api/cadastrarAluno', {
      method: 'POST',
      body: JSON.stringify(dados)
    });

    if (resp.status === 201) {
      const aluno = await resp.json();
      this.mostrarToast(`Aluno "${aluno.nome}" cadastrado com sucesso!`, 'success');
      document.getElementById('form-cadastrar-aluno').reset();
      await this.carregarAlunos();
    } else {
      const err = await resp.json().catch(() => ({ mensagem: 'Erro desconhecido ao cadastrar aluno' }));
      this.tratarErrosApi(err, 'alerta-erro-aluno');
    }
  }

  abrirModalStatusAluno(id, nome, statusAtual) {
    document.getElementById('status-aluno-id').value = id;
    document.getElementById('status-aluno-nome').innerHTML = `Aluno: <strong>${nome}</strong>`;
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
        this.alunos.map(a => `<option value="${a.id}">${a.nome} (${a.cpf})</option>`).join('');
      if (valorAtual) sel.value = valorAtual;
    });
  }

  abrirFichasDoAluno(alunoId) {
    this.navegarParaAba('tab-treinos');
    const selectFiltro = document.getElementById('select-filtro-aluno-treino');
    if (selectFiltro) selectFiltro.value = alunoId;
    const selectCriar = document.getElementById('treino-aluno-select');
    if (selectCriar) selectCriar.value = alunoId;
    this.carregarFichasDoAluno(alunoId);
  }

  abrirMensalidadesDoAluno(alunoId) {
    this.navegarParaAba('tab-financeiro');
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
    div.innerHTML = `
      <input type="text" class="form-control" placeholder="Exercício (ex: Supino)"
             value="${nome}" required data-field="nome">
      <input type="text" class="form-control" placeholder="Grupo (ex: Peito)"
             value="${grupo}" data-field="grupo">
      <input type="number" class="form-control" placeholder="Séries"
             value="${series}" min="1" required data-field="series">
      <input type="number" class="form-control" placeholder="Reps"
             value="${reps}" min="1" required data-field="reps">
      <input type="number" step="0.5" class="form-control" placeholder="Sugerido (kg)"
             value="${carga}" min="0" data-field="carga">
      <button type="button" class="btn btn-xs btn-danger btn-remove-row" onclick="document.getElementById('${id}').remove()">✕</button>
    `;
    container.appendChild(div);
  }

  async criarFichaTreino(dados) {
    this.ocultarAlerta('alerta-erro-ficha');
    if (!dados.alunoId) {
      this.mostrarAlerta('alerta-erro-ficha', 'Selecione um aluno para vincular a ficha de treino.');
      this.mostrarToast('Selecione um aluno para vincular a ficha.', 'warning');
      return;
    }

    const resp = await this.apiRequest('/api/criarFichaTreino', {
      method: 'POST',
      body: JSON.stringify(dados)
    });

    if (resp.status === 201) {
      const ficha = await resp.json();
      this.mostrarToast(`Ficha "${ficha.titulo}" criada com sucesso!`, 'success');
      document.getElementById('form-criar-ficha').reset();
      document.getElementById('exercicios-container').innerHTML = '';
      this.adicionarExercicioLinha();

      // Seleciona o aluno no filtro lateral e carrega as fichas automaticamente
      const selectFiltro = document.getElementById('select-filtro-aluno-treino');
      if (selectFiltro) selectFiltro.value = dados.alunoId;
      await this.carregarFichasDoAluno(dados.alunoId);
    } else {
      const err = await resp.json().catch(() => ({ mensagem: 'Falha ao criar ficha de treino' }));
      this.tratarErrosApi(err, 'alerta-erro-ficha');
    }
  }

  async carregarFichasDoAluno(alunoId) {
    const container = document.getElementById('fichas-container');
    if (!container) return;

    if (!alunoId) {
      container.innerHTML = '<p class="text-muted text-center py-4">Selecione um aluno acima para ver suas fichas e registrar execuções.</p>';
      return;
    }

    container.innerHTML = '<p class="text-muted text-center py-4">⏳ Carregando fichas do aluno...</p>';

    try {
      const resp = await this.apiRequest(`/api/listarFichasAluno/${alunoId}`);
      if (resp.ok) {
        const fichas = await resp.json();
        this.renderizarFichas(fichas, container, alunoId);
      } else {
        const err = await resp.json().catch(() => ({ mensagem: 'Falha ao buscar fichas de treino.' }));
        this.tratarErrosApi(err);
        container.innerHTML = `
          <div class="alert alert-danger mt-3">
            <span class="alert-icon">⚠️</span>
            <div class="alert-content">
              <strong>Não foi possível carregar as fichas</strong>
              <p>${err.mensagem || 'Erro na resposta do servidor'}</p>
              ${err.codigo ? `<small class="text-muted">Código: ${err.codigo}</small>` : ''}
              <div class="mt-2">
                <button class="btn btn-xs btn-outline" onclick="app.carregarFichasDoAluno('${alunoId}')">Tentar Novamente</button>
              </div>
            </div>
          </div>
        `;
      }
    } catch (e) {
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
      const badgeStatus = f.status === 'CONCLUIDO'
        ? '<span class="badge badge-success">Concluído</span>'
        : '<span class="badge badge-warning">Pendente</span>';

      const itensHtml = f.itens.map(item => `
        <div class="exercicio-item-card" data-item-id="${item.id}">
          <div>
            <strong>${item.nomeExercicio}</strong><br>
            <small class="text-muted">${item.grupoMuscular || 'Geral'}</small>
          </div>
          <div>${item.series}x${item.repeticoes}</div>
          <div title="Carga sugerida">Sug.: ${item.cargaSugerida}kg</div>
          <div>
            <input type="number" step="0.5" class="form-control form-control-sm input-carga-real"
                   value="${item.cargaUsada > 0 ? item.cargaUsada : item.cargaSugerida}"
                   placeholder="Real kg">
          </div>
          <div>
            <input type="checkbox" class="chk-concluido" ${item.concluido ? 'checked' : ''}>
          </div>
        </div>
      `).join('');

      return `
        <div class="ficha-card" id="card-ficha-${f.id}">
          <div class="ficha-header">
            <div>
              <strong>${f.titulo}</strong>
              <small class="text-muted d-block">Objetivo: ${f.objetivo} · ${f.dataCriacao || 'Hoje'}</small>
            </div>
            ${badgeStatus}
          </div>
          <div class="exercicios-list mb-3">
            <div class="text-xs text-muted mb-1" style="display:grid; grid-template-columns: 2fr 1fr 1fr 1fr 60px; padding: 0 0.85rem;">
              <span>Exercício</span><span>Séries</span><span>Sugestão</span><span>Carga Real</span><span>Feito</span>
            </div>
            ${itensHtml}
          </div>
          <div class="card-footer-actions flex-between">
            <small class="text-muted">${f.dataConclusao ? 'Concluído em: ' + f.dataConclusao : 'Pendente de execução'}</small>
            <button class="btn btn-sm btn-primary" onclick="app.salvarExecucao('${f.id}')">
              Salvar Execução
            </button>
          </div>
        </div>
      `;
    }).join('');
  }

  async salvarExecucao(fichaId) {
    const card = document.getElementById(`card-ficha-${fichaId}`);
    if (!card) return;

    const itens = [];
    card.querySelectorAll('.exercicio-item-card').forEach(el => {
      itens.push({
        itemId: el.dataset.itemId,
        cargaUsada: parseFloat(el.querySelector('.input-carga-real').value) || 0,
        concluido: el.querySelector('.chk-concluido').checked
      });
    });

    const resp = await this.apiRequest(`/api/registrarExecucaoTreino/${fichaId}`, {
      method: 'POST',
      body: JSON.stringify({ itens })
    });

    if (resp.ok) {
      const fichaAtualizada = await resp.json();
      this.mostrarToast('Execução do treino registrada! Cargas reais salvas.', 'success');
      const alunoId = document.getElementById('select-filtro-aluno-treino').value;
      if (alunoId) await this.carregarFichasDoAluno(alunoId);
    } else {
      const err = await resp.json().catch(() => ({ mensagem: 'Falha ao salvar execução do treino' }));
      this.tratarErrosApi(err);
    }
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
        this.renderizarResultadoIa(ficha, container, dados.alunoId);
      } else {
        const err = await resp.json().catch(() => ({ mensagem: 'Falha na geração com IA' }));
        this.tratarErrosApi(err, 'alerta-erro-ia');
        container.innerHTML = `<div class="alert alert-danger mt-3">⚠️ ${err.mensagem || 'Falha ao gerar treino.'}</div>`;
      }
    } catch (e) {
      container.innerHTML = `<div class="alert alert-danger mt-3">❌ Erro de comunicação: ${e.message}</div>`;
    } finally {
      btn.disabled = false;
      btn.textContent = 'Gerar Treino com IA ✨';
    }
  }

  renderizarResultadoIa(ficha, container, alunoId) {
    const itensHtml = ficha.itens.map(i => `
      <div class="exercicio-item-card" style="margin-bottom:0.4rem;">
        <div><strong>${i.nomeExercicio}</strong> <small class="text-muted">(${i.grupoMuscular || 'Geral'})</small></div>
        <div>${i.series} séries x ${i.repeticoes} reps</div>
        <div><strong>${i.cargaSugerida} kg</strong> (sugerido)</div>
      </div>
    `).join('');

    container.innerHTML = `
      <div class="ficha-card" style="border-color: rgba(99, 102, 241, 0.4);">
        <div class="ficha-header">
          <div>
            <strong style="color:var(--accent-glow);">${ficha.titulo}</strong>
            <small class="text-muted d-block">Objetivo: ${ficha.objetivo} · Gerado com IA</small>
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

    const resp = await this.apiRequest('/api/gerarMensalidade', {
      method: 'POST',
      body: JSON.stringify(dados)
    });

    if (resp.status === 201) {
      const mensalidade = await resp.json();
      this.mostrarToast(`Mensalidade de R$ ${mensalidade.valor.toFixed(2)} gerada com sucesso!`, 'success');
      document.getElementById('form-gerar-mensalidade').reset();
      const selectFiltro = document.getElementById('select-filtro-aluno-fin');
      if (selectFiltro) selectFiltro.value = dados.alunoId;
      await this.carregarMensalidadesDoAluno(dados.alunoId);
    } else {
      const err = await resp.json().catch(() => ({ mensagem: 'Falha ao gerar mensalidade' }));
      this.tratarErrosApi(err, 'alerta-erro-mensalidade');
    }
  }

  async carregarMensalidadesDoAluno(alunoId) {
    const tbody = document.getElementById('tbody-mensalidades');
    if (!tbody) return;

    if (!alunoId) {
      tbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted py-4">Selecione um aluno acima para listar cobranças.</td></tr>';
      return;
    }

    tbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted py-4">⏳ Carregando mensalidades do aluno...</td></tr>';

    try {
      const resp = await this.apiRequest(`/api/listarMensalidadesAluno/${alunoId}`);
      if (resp.ok) {
        const lista = await resp.json();
        this.renderizarMensalidades(lista, tbody, alunoId);
      } else {
        const err = await resp.json().catch(() => ({ mensagem: 'Falha ao buscar mensalidades.' }));
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
        ? `<button class="btn btn-xs btn-primary" onclick="app.abrirModalPagamento('${m.id}', ${m.valor})">Quitar Pagamento</button>`
        : `<small class="text-muted">via ${m.formaPagamento || 'PIX'}</small>`;

      return `
        <tr>
          <td>${m.descricao || 'Mensalidade'}</td>
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
    document.getElementById('pagamento-detalhes').textContent = `Valor a pagar: R$ ${valor.toFixed(2)}`;
    this.abrirModal('modal-pagamento');
  }

  async confirmarPagamento(id, formaPagamento) {
    const resp = await this.apiRequest(`/api/registrarPagamentoMensalidade/${id}`, {
      method: 'POST',
      body: JSON.stringify({ formaPagamento })
    });

    if (resp.ok) {
      this.mostrarToast(`Pagamento registrado com sucesso via ${formaPagamento}!`, 'success');
      this.fecharModal('modal-pagamento');
      const alunoId = document.getElementById('select-filtro-aluno-fin').value;
      if (alunoId) await this.carregarMensalidadesDoAluno(alunoId);
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

  navegarParaAba(tabId) {
    document.querySelectorAll('.nav-tab').forEach(t => {
      t.classList.toggle('active', t.dataset.tab === tabId);
    });
    document.querySelectorAll('.tab-pane').forEach(p => {
      p.classList.toggle('active', p.id === tabId);
    });
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
    html += `<strong>${mensagem}</strong>`;

    if (listaErros && Object.keys(listaErros).length > 0) {
      html += '<ul>';
      for (const [campo, desc] of Object.entries(listaErros)) {
        html += `<li><strong>${campo}:</strong> ${desc}</li>`;
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

    // Modal de Login
    document.getElementById('btn-login-modal')?.addEventListener('click', () => {
      this.abrirModal('modal-login');
    });

    document.getElementById('btn-quick-token')?.addEventListener('click', async () => {
      await this.obterTokenRapido();
      this.fecharModal('modal-login');
    });

    document.getElementById('btn-clear-token')?.addEventListener('click', () => {
      this.setToken(null);
      this.mostrarToast('Sessão encerrada. Faça login para continuar.', 'warning');
      this.fecharModal('modal-login');
    });

    document.getElementById('form-login')?.addEventListener('submit', (e) => {
      e.preventDefault();
      const user = document.getElementById('login-username').value;
      const pass = document.getElementById('login-senha').value;
      this.realizarLogin(user, pass);
    });

    // Form Aluno
    document.getElementById('form-cadastrar-aluno')?.addEventListener('submit', (e) => {
      e.preventDefault();
      const dados = {
        nome: document.getElementById('aluno-nome').value.trim(),
        cpf: document.getElementById('aluno-cpf').value.trim(),
        email: document.getElementById('aluno-email').value.trim(),
        telefone: document.getElementById('aluno-telefone').value.trim() || null,
        peso: parseFloat(document.getElementById('aluno-peso').value) || null,
        altura: parseFloat(document.getElementById('aluno-altura').value) || null
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
