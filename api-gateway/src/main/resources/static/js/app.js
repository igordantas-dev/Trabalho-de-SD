/**
 * GymDist Pro — Lógica do Frontend SPA
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

    // Se não tiver token, gera automaticamente o token de laboratório para início imediato
    if (!this.token) {
      await this.obterTokenRapido();
    }

    // Carrega dados iniciais
    await this.carregarAlunos();
    this.adicionarExercicioLinha('Supino Reto', 'Peito', 4, 10, 30);
    this.adicionarExercicioLinha('Tríceps Corda', 'Tríceps', 3, 12, 15);
  }

  // ─── Autenticação & JWT ───────────────────────────────────────────────────

  atualizarStatusJwt() {
    const indicator = document.getElementById('jwt-indicator');
    const label = document.getElementById('jwt-label');
    const textarea = document.getElementById('token-textarea');

    if (this.token) {
      indicator.classList.add('active');
      label.textContent = 'JWT: Ativo';
      textarea.value = this.token;
    } else {
      indicator.classList.remove('active');
      label.textContent = 'JWT: Ausente (401)';
      textarea.value = '';
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
      const resp = await fetch('/api/gerar-token');
      if (resp.ok) {
        const data = await resp.json();
        this.setToken(data.token);
        this.mostrarToast('Token JWT de laboratório gerado com sucesso!', 'success');
      }
    } catch (e) {
      console.warn('Erro ao obter token de laboratório:', e);
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
        this.mostrarToast(`Bem-vindo, ${data.usuario}! Token JWT emitido.`, 'success');
        this.fecharModal('modal-login');
      } else {
        const err = await resp.json();
        this.mostrarToast(`Falha no login: ${err.mensagem || 'Credenciais inválidas'}`, 'danger');
      }
    } catch (e) {
      this.mostrarToast(`Erro de conexão com o Gateway: ${e.message}`, 'danger');
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

      // Se não autenticado (401)
      if (response.status === 401) {
        this.setToken(null);
        this.mostrarToast('Sessão expirada ou sem token (HTTP 401). Faça login novamente.', 'danger');
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
      const resp = await this.apiRequest('/api/listar-alunos');
      if (resp.ok) {
        this.alunos = await resp.json();
        this.renderizarTabelaAlunos();
        this.atualizarSelectsAlunos();
        this.atualizarMetricasDashboard();
      }
    } catch (e) {
      console.error('Erro ao listar alunos:', e);
    }
  }

  renderizarTabelaAlunos() {
    const tbody = document.getElementById('tbody-alunos');
    if (!this.alunos || this.alunos.length === 0) {
      tbody.innerHTML = `<tr><td colspan="5" class="text-center text-muted py-4">Nenhum aluno cadastrado no momento.</td></tr>`;
      return;
    }

    tbody.innerHTML = this.alunos.map(aluno => {
      const statusBadge = aluno.status === 'ATIVO' 
        ? '<span class="badge badge-success">ATIVO</span>'
        : aluno.status === 'INADIMPLENTE' 
          ? '<span class="badge badge-warning">INADIMPLENTE</span>' 
          : '<span class="badge badge-danger">INATIVO</span>';

      const biometria = (aluno.peso ? `${aluno.peso}kg` : '-') + ' / ' + (aluno.altura ? `${aluno.altura}m` : '-');

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
            <div class="flex-column gap-2">
              <button class="btn btn-xs btn-outline" onclick="app.alternarStatusAluno('${aluno.id}', '${aluno.status}')">
                Mudar Status
              </button>
            </div>
          </td>
        </tr>
      `;
    }).join('');
  }

  async cadastrarAluno(dados) {
    const resp = await this.apiRequest('/api/cadastrar-aluno', {
      method: 'POST',
      body: JSON.stringify(dados)
    });

    if (resp.status === 201) {
      const aluno = await resp.json();
      this.mostrarToast(`Aluno ${aluno.nome} cadastrado com sucesso! (ID: ${aluno.id.slice(0, 8)}...)`, 'success');
      document.getElementById('form-cadastrar-aluno').reset();
      await this.carregarAlunos();
    } else {
      const err = await resp.json();
      this.tratarErrosApi(err);
    }
  }

  async alternarStatusAluno(id, statusAtual) {
    const novoStatus = statusAtual === 'ATIVO' ? 'INATIVO' : 'ATIVO';
    const resp = await this.apiRequest(`/api/atualizar-status-aluno/${id}`, {
      method: 'PATCH',
      body: JSON.stringify({ status: novoStatus })
    });

    if (resp.ok) {
      this.mostrarToast(`Status alterado para ${novoStatus}!`, 'success');
      await this.carregarAlunos();
    } else {
      const err = await resp.json();
      this.tratarErrosApi(err);
    }
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

  // ─── Módulo: Fichas de Treino & Execução ───────────────────────────────────

  adicionarExercicioLinha(nome = '', grupo = '', series = 3, reps = 12, carga = 0) {
    const container = document.getElementById('exercicios-container');
    const id = 'ex_' + Math.random().toString(36).substring(2, 9);

    const div = document.createElement('div');
    div.className = 'exercicio-row';
    div.id = id;
    div.innerHTML = `
      <input type="text" class="form-control col" placeholder="Exercício" value="${nome}" required data-field="nome">
      <input type="text" class="form-control col" placeholder="Músculo" value="${grupo}" data-field="grupo">
      <input type="number" class="form-control" style="width: 70px" placeholder="Séries" value="${series}" required data-field="series">
      <input type="number" class="form-control" style="width: 70px" placeholder="Reps" value="${reps}" required data-field="reps">
      <input type="number" step="0.5" class="form-control" style="width: 80px" placeholder="Kg" value="${carga}" data-field="carga">
      <button type="button" class="btn btn-xs btn-outline-danger" onclick="document.getElementById('${id}').remove()">✕</button>
    `;
    container.appendChild(div);
  }

  async criarFichaTreino(dados) {
    const resp = await this.apiRequest('/api/criar-ficha-treino', {
      method: 'POST',
      body: JSON.stringify(dados)
    });

    if (resp.status === 201) {
      const ficha = await resp.json();
      this.mostrarToast(`Ficha "${ficha.titulo}" criada com sucesso!`, 'success');
      document.getElementById('form-criar-ficha').reset();
      document.getElementById('select-filtro-aluno-treino').value = dados.alunoId;
      await this.carregarFichasDoAluno(dados.alunoId);
    } else {
      const err = await resp.json();
      this.tratarErrosApi(err);
    }
  }

  async carregarFichasDoAluno(alunoId) {
    const container = document.getElementById('fichas-container');
    if (!alunoId) {
      container.innerHTML = '<p class="text-muted text-center py-4">Selecione um aluno acima.</p>';
      return;
    }

    try {
      const resp = await this.apiRequest(`/api/listar-fichas-aluno/${alunoId}`);
      if (resp.ok) {
        const fichas = await resp.json();
        this.renderizarFichas(fichas, container);
      }
    } catch (e) {
      container.innerHTML = '<p class="text-danger">Erro ao carregar fichas.</p>';
    }
  }

  renderizarFichas(fichas, container) {
    if (!fichas || fichas.length === 0) {
      container.innerHTML = '<p class="text-muted text-center py-4">Nenhuma ficha encontrada para este aluno.</p>';
      return;
    }

    container.innerHTML = fichas.map(f => {
      const badgeStatus = f.status === 'CONCLUIDO' 
        ? '<span class="badge badge-success">CONCLUÍDO</span>'
        : '<span class="badge badge-warning">PENDENTE</span>';

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
              <small class="text-muted d-block">Objetivo: ${f.objetivo} · Criado em: ${f.dataCriacao || 'Hoje'}</small>
            </div>
            ${badgeStatus}
          </div>
          <div class="exercicios-list mb-3">
            <div class="text-xs text-muted mb-1" style="display:grid; grid-template-columns: 2fr 1fr 1fr 1fr 60px; padding: 0 0.85rem;">
              <span>Exercício</span><span>Séries</span><span>Sugestão</span><span>Carga Real</span><span>Feito</span>
            </div>
            ${itensHtml}
          </div>
          <div class="flex-between">
            <small class="text-muted">${f.dataConclusao ? 'Concluído em: ' + f.dataConclusao : 'Aguardando execução'}</small>
            <button class="btn btn-sm btn-primary" onclick="app.registrarExecucao('${f.id}')">
              Salvar Cargas & Concluir
            </button>
          </div>
        </div>
      `;
    }).join('');
  }

  async registrarExecucao(fichaId) {
    const card = document.getElementById(`card-ficha-${fichaId}`);
    if (!card) return;

    const itensDivs = card.querySelectorAll('.exercicio-item-card');
    const itens = [];

    itensDivs.forEach(div => {
      const itemId = div.dataset.itemId;
      const cargaUsada = parseFloat(div.querySelector('.input-carga-real').value) || 0.0;
      const concluido = div.querySelector('.chk-concluido').checked;

      itens.push({ itemId, cargaUsada, concluido });
    });

    const resp = await this.apiRequest(`/api/registrar-execucao-treino/${fichaId}`, {
      method: 'POST',
      body: JSON.stringify({ itens })
    });

    if (resp.ok) {
      const atualizada = await resp.json();
      this.mostrarToast(`Execução da ficha registrada! Cargas salvas com sucesso.`, 'success');
      const alunoId = document.getElementById('select-filtro-aluno-treino').value;
      await this.carregarFichasDoAluno(alunoId);
    } else {
      const err = await resp.json();
      this.tratarErrosApi(err);
    }
  }

  // ─── Módulo: Assistente IA ───────────────────────────────────────────────

  async gerarTreinoComIa(dados) {
    const badge = document.getElementById('ia-status-badge');
    const resultado = document.getElementById('ia-resultado-container');
    const btn = document.getElementById('btn-submit-ia');

    badge.className = 'badge badge-warning';
    badge.textContent = 'Processando com IA...';
    btn.disabled = true;

    resultado.innerHTML = `
      <div class="text-center py-4">
        <div style="font-size: 2rem; animation: pulse 1s infinite;">✨</div>
        <p class="text-muted mt-2">Consultando biometria e estruturando exercícios...</p>
      </div>
    `;

    try {
      const resp = await this.apiRequest('/api/gerar-treino-ia', {
        method: 'POST',
        body: JSON.stringify(dados)
      });

      if (resp.ok) {
        const ficha = await resp.json();
        badge.className = 'badge badge-success';
        badge.textContent = 'Gerado & Salvo no BD';
        this.mostrarToast(`Treino IA "${ficha.titulo}" gerado e salvo no PostgreSQL!`, 'success');

        resultado.innerHTML = `
          <div class="ficha-card" style="border-color: rgba(236, 72, 153, 0.4);">
            <div class="ficha-header">
              <div>
                <strong style="color: #f472b6;">${ficha.titulo}</strong>
                <small class="text-muted d-block">Objetivo: ${ficha.objetivo} · ID: ${ficha.id}</small>
              </div>
              <span class="badge badge-success">Salvo no Banco</span>
            </div>
            <div class="exercicios-list mt-2">
              ${ficha.itens.map(i => `
                <div class="exercicio-item-card" style="grid-template-columns: 2fr 1fr 1fr;">
                  <div>
                    <strong>${i.nomeExercicio}</strong>
                    <small class="text-muted d-block">${i.grupoMuscular}</small>
                  </div>
                  <div>${i.series} séries de ${i.repeticoes} reps</div>
                  <div><strong>${i.cargaSugerida} kg</strong> recomendados</div>
                </div>
              `).join('')}
            </div>
          </div>
        `;

        this.carregarAlunos();
      } else {
        const err = await resp.json();
        badge.className = 'badge badge-danger';
        badge.textContent = 'Falha';
        this.tratarErrosApi(err);
      }
    } finally {
      btn.disabled = false;
    }
  }

  // ─── Módulo: Financeiro (Mensalidades) ────────────────────────────────────

  async gerarMensalidade(dados) {
    const resp = await this.apiRequest('/api/gerar-mensalidade', {
      method: 'POST',
      body: JSON.stringify(dados)
    });

    if (resp.status === 201) {
      const mensalidade = await resp.json();
      this.mostrarToast(`Mensalidade de R$ ${mensalidade.valor.toFixed(2)} gerada com sucesso!`, 'success');
      document.getElementById('form-gerar-mensalidade').reset();
      document.getElementById('select-filtro-aluno-fin').value = dados.alunoId;
      await this.carregarMensalidadesDoAluno(dados.alunoId);
    } else {
      const err = await resp.json();
      this.tratarErrosApi(err);
    }
  }

  async carregarMensalidadesDoAluno(alunoId) {
    const tbody = document.getElementById('tbody-mensalidades');
    if (!alunoId) {
      tbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted py-4">Selecione um aluno acima.</td></tr>';
      return;
    }

    try {
      const resp = await this.apiRequest(`/api/listar-mensalidades-aluno/${alunoId}`);
      if (resp.ok) {
        const lista = await resp.json();
        this.renderizarMensalidades(lista, tbody);
      }
    } catch (e) {
      tbody.innerHTML = '<tr><td colspan="5" class="text-danger text-center">Erro ao carregar mensalidades.</td></tr>';
    }
  }

  renderizarMensalidades(mensalidades, tbody) {
    if (!mensalidades || mensalidades.length === 0) {
      tbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted py-4">Nenhuma cobrança registrada para este aluno.</td></tr>';
      return;
    }

    tbody.innerHTML = mensalidades.map(m => {
      const statusBadge = m.status === 'PAGO'
        ? '<span class="badge badge-success">PAGO</span>'
        : m.status === 'ATRASADO'
          ? '<span class="badge badge-danger">ATRASADO</span>'
          : '<span class="badge badge-warning">PENDENTE</span>';

      const botaoAcao = m.status !== 'PAGO'
        ? `<button class="btn btn-xs btn-primary" onclick="app.abrirModalPagamento('${m.id}', ${m.valor})">Pagar</button>`
        : `<small class="text-muted">Pago via ${m.formaPagamento || 'PIX'}</small>`;

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
    document.getElementById('pagamento-detalhes').textContent = `Valor a quitar: R$ ${valor.toFixed(2)}`;
    this.abrirModal('modal-pagamento');
  }

  async confirmarPagamento(id, formaPagamento) {
    const resp = await this.apiRequest(`/api/registrar-pagamento-mensalidade/${id}`, {
      method: 'POST',
      body: JSON.stringify({ formaPagamento })
    });

    if (resp.ok) {
      this.mostrarToast(`Pagamento via ${formaPagamento} registrado com sucesso!`, 'success');
      this.fecharModal('modal-pagamento');
      const alunoId = document.getElementById('select-filtro-aluno-fin').value;
      await this.carregarMensalidadesDoAluno(alunoId);
      await this.carregarAlunos();
    } else {
      const err = await resp.json();
      this.tratarErrosApi(err);
    }
  }

  // ─── Módulo: Demo Ponta a Ponta (Avaliação Trabalho 2) ─────────────────────

  async executarDemo(tipo) {
    const consoleOutput = document.getElementById('demo-console-output');
    const statusPill = document.getElementById('demo-status-pill');

    consoleOutput.textContent = 'Enviando requisição para o API Gateway...';
    statusPill.textContent = 'Enviando...';
    statusPill.className = 'badge badge-secondary';

    let url, method, headers = {}, body = null;

    if (tipo === '201') {
      // Sucesso com JWT
      url = '/api/cadastrar-aluno';
      method = 'POST';
      headers['Content-Type'] = 'application/json';
      headers['Authorization'] = `Bearer ${this.token || 'token-padrao'}`;
      body = JSON.stringify({
        nome: "Aluno Demo Avaliação",
        cpf: "999." + Math.floor(100 + Math.random() * 900) + ".111-00",
        email: "demo_" + Math.random().toString(36).substring(7) + "@email.com",
        peso: 75.0,
        altura: 1.75
      });
    } else if (tipo === '401') {
      // Barrado no Gateway (Sem token JWT)
      url = '/api/listar-alunos';
      method = 'GET';
      headers['Content-Type'] = 'application/json';
      // NÃO envia Authorization
    } else if (tipo === '400') {
      // Bean Validation inválido (com JWT válido)
      url = '/api/cadastrar-aluno';
      method = 'POST';
      headers['Content-Type'] = 'application/json';
      headers['Authorization'] = `Bearer ${this.token || 'token-padrao'}`;
      body = JSON.stringify({
        nome: "", // inválido!
        cpf: "",  // inválido!
        email: "email-invalido"
      });
    } else if (tipo === '404') {
      // Not Found via gRPC
      url = '/api/buscar-aluno/00000000-0000-0000-0000-000000000000';
      method = 'GET';
      headers['Content-Type'] = 'application/json';
      headers['Authorization'] = `Bearer ${this.token || 'token-padrao'}`;
    }

    try {
      const resp = await fetch(url, { method, headers, body });
      const status = resp.status;
      let textResp;
      try {
        const json = await resp.json();
        textResp = JSON.stringify(json, null, 2);
      } catch {
        textResp = await resp.text();
      }

      statusPill.textContent = `HTTP ${status} ${resp.statusText}`;
      if (status >= 200 && status < 300) statusPill.className = 'badge badge-success';
      else if (status === 401) statusPill.className = 'badge badge-danger';
      else if (status === 400) statusPill.className = 'badge badge-warning';
      else statusPill.className = 'badge badge-secondary';

      consoleOutput.textContent = 
        `>>> REQUISIÇÃO:\n${method} ${url}\nHeaders: ${JSON.stringify(headers)}\nBody: ${body || 'nenhum'}\n\n` +
        `<<< RESPOSTA DO GATEWAY:\nStatus: ${status} ${resp.statusText}\nBody:\n${textResp}`;

    } catch (e) {
      consoleOutput.textContent = `Erro: ${e.message}`;
      statusPill.textContent = 'Erro de Conexão';
      statusPill.className = 'badge badge-danger';
    }
  }

  // ─── Dashboard & UI Helpers ───────────────────────────────────────────────

  atualizarMetricasDashboard() {
    const totalAlunos = this.alunos.length;
    document.getElementById('dash-total-alunos').textContent = totalAlunos;
  }

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

  mostrarToast(mensagem, tipo = 'info') {
    const container = document.getElementById('toast-container');
    const toast = document.createElement('div');
    toast.className = `toast ${tipo}`;
    toast.textContent = mensagem;

    container.appendChild(toast);
    setTimeout(() => {
      toast.style.opacity = '0';
      setTimeout(() => toast.remove(), 300);
    }, 4000);
  }

  tratarErrosApi(err) {
    if (err.erros) {
      const campos = Object.entries(err.erros).map(([campo, msg]) => `${campo}: ${msg}`).join('; ');
      this.mostrarToast(`Erro de validação (400): ${campos}`, 'warning');
    } else {
      this.mostrarToast(err.mensagem || 'Ocorreu um erro no Gateway.', 'danger');
    }
  }

  setupEventListeners() {
    // Abas
    document.querySelectorAll('.nav-tab').forEach(tab => {
      tab.addEventListener('click', () => this.navegarParaAba(tab.dataset.tab));
    });

    // Modal de Login
    document.getElementById('btn-login-modal').addEventListener('click', () => {
      this.abrirModal('modal-login');
    });

    document.getElementById('btn-quick-token').addEventListener('click', async () => {
      await this.obterTokenRapido();
      this.fecharModal('modal-login');
    });

    document.getElementById('btn-clear-token').addEventListener('click', () => {
      this.setToken(null);
      this.mostrarToast('Token limpo! Próximas requisições serão rejeitadas com 401.', 'warning');
      this.fecharModal('modal-login');
    });

    document.getElementById('form-login').addEventListener('submit', (e) => {
      e.preventDefault();
      const user = document.getElementById('login-username').value;
      const pass = document.getElementById('login-senha').value;
      this.realizarLogin(user, pass);
    });

    // Form Aluno
    document.getElementById('form-cadastrar-aluno').addEventListener('submit', (e) => {
      e.preventDefault();
      const dados = {
        nome: document.getElementById('aluno-nome').value,
        cpf: document.getElementById('aluno-cpf').value,
        email: document.getElementById('aluno-email').value,
        telefone: document.getElementById('aluno-telefone').value || null,
        peso: parseFloat(document.getElementById('aluno-peso').value) || null,
        altura: parseFloat(document.getElementById('aluno-altura').value) || null
      };
      this.cadastrarAluno(dados);
    });

    document.getElementById('btn-refresh-alunos').addEventListener('click', () => {
      this.carregarAlunos();
      this.mostrarToast('Lista de alunos atualizada.', 'info');
    });

    // Form Ficha Manual
    document.getElementById('btn-add-exercicio').addEventListener('click', () => {
      this.adicionarExercicioLinha();
    });

    document.getElementById('form-criar-ficha').addEventListener('submit', (e) => {
      e.preventDefault();
      const alunoId = document.getElementById('treino-aluno-select').value;
      const titulo = document.getElementById('treino-titulo').value;
      const objetivo = document.getElementById('treino-objetivo').value;

      const itens = [];
      document.querySelectorAll('#exercicios-container .exercicio-row').forEach(row => {
        itens.push({
          nomeExercicio: row.querySelector('[data-field="nome"]').value,
          grupoMuscular: row.querySelector('[data-field="grupo"]').value,
          series: parseInt(row.querySelector('[data-field="series"]').value) || 3,
          repeticoes: parseInt(row.querySelector('[data-field="reps"]').value) || 10,
          cargaSugerida: parseFloat(row.querySelector('[data-field="carga"]').value) || 0
        });
      });

      this.criarFichaTreino({ alunoId, titulo, objetivo, itens });
    });

    document.getElementById('select-filtro-aluno-treino').addEventListener('change', (e) => {
      this.carregarFichasDoAluno(e.target.value);
    });

    // Form Gerador IA
    document.getElementById('form-gerar-ia').addEventListener('submit', (e) => {
      e.preventDefault();
      const dados = {
        alunoId: document.getElementById('ia-aluno-select').value,
        objetivo: document.getElementById('ia-objetivo').value,
        nivel: document.getElementById('ia-nivel').value,
        focoMuscular: document.getElementById('ia-foco').value || null,
        observacoes: document.getElementById('ia-observacoes').value || null
      };
      this.gerarTreinoComIa(dados);
    });

    // Form Mensalidade
    document.getElementById('form-gerar-mensalidade').addEventListener('submit', (e) => {
      e.preventDefault();
      const dados = {
        alunoId: document.getElementById('fin-aluno-select').value,
        valor: parseFloat(document.getElementById('fin-valor').value) || 0,
        dataVencimento: document.getElementById('fin-vencimento').value,
        descricao: document.getElementById('fin-descricao').value || null
      };
      this.gerarMensalidade(dados);
    });

    document.getElementById('select-filtro-aluno-fin').addEventListener('change', (e) => {
      this.carregarMensalidadesDoAluno(e.target.value);
    });

    // Form Pagamento
    document.getElementById('form-quitar-mensalidade').addEventListener('submit', (e) => {
      e.preventDefault();
      const id = document.getElementById('pagamento-mensalidade-id').value;
      const forma = document.getElementById('pagamento-forma').value;
      this.confirmarPagamento(id, forma);
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
