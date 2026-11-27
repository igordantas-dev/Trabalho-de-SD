/**
 * GymFlow — Portal do Aluno
 * Visualização e execução de treinos próprios e acompanhamento de mensalidades.
 */

class AlunoApp {
  constructor() {
    this.token = localStorage.getItem('gymdist_aluno_jwt') || null;
    this.alunoId = localStorage.getItem('gymdist_aluno_id') || null;
    this.alunoDados = null;
    this.alunosDisponiveis = [];

    this.init();
  }

  async init() {
    this.setupEventListeners();
    this.mostrarTelaLogin();
    if (this.token) {
      try {
        const resp = await this.apiRequest('/api/sessao');
        if (!resp.ok) return;
        const sessao = await resp.json();
        if (sessao.perfil !== 'ALUNO' || !sessao.alunoId) { this.logout(false); return; }
        this.alunoId = sessao.alunoId;
        await this.carregarDadosDoAluno();
      } catch (error) { this.mostrarToast(error.message, 'danger'); }
    }
  }

  mostrarTelaLogin() {
    document.getElementById('view-login-aluno').style.display = 'block';
    document.getElementById('view-portal-aluno').style.display = 'none';
    document.getElementById('header-aluno-logado').style.display = 'none';
  }

  mostrarPortalAluno() {
    document.getElementById('view-login-aluno').style.display = 'none';
    document.getElementById('view-portal-aluno').style.display = 'block';
    document.getElementById('header-aluno-logado').style.display = 'flex';
  }

  // ─── Chamadas de API com JWT ──────────────────────────────────────────────

  async apiRequest(endpoint, options = {}) {
    const headers = options.headers || {};
    headers['Content-Type'] = 'application/json';

    if (this.token) {
      headers['Authorization'] = `Bearer ${this.token}`;
    }

    try {
      const response = await GymHttp.request(endpoint, {
        ...options,
        headers
      });

      if (response.status === 401) {
        this.logout(false);
        this.mostrarToast('Sua sessão expirou. Entre novamente.', 'warning');
      }

      return response;
    } catch (err) {
      this.mostrarToast(`Falha de rede: ${err.message}`, 'danger');
      throw err;
    }
  }

  // ─── Autenticação do Aluno ───────────────────────────────────────────────

  async login(email, senha) {
    const botao = document.getElementById('btn-entrar-aluno');
    const alerta = document.getElementById('alerta-login-aluno');
    if (botao.disabled) return;
    botao.disabled = true; alerta.classList.add('d-none');
    try {
      const resp = await GymHttp.request('/api/autenticar', {
        method:'POST', headers:{'Content-Type':'application/json'},
        body:JSON.stringify({username:email.trim(), senha, perfil:'ALUNO'})
      });
      const data = await resp.json();
      if (!resp.ok) { alerta.textContent = data.mensagem; alerta.classList.remove('d-none'); return; }
      if (data.perfil !== 'ALUNO' || !data.alunoId || !data.token) throw new Error('Esta conta não pode acessar o Portal do Aluno.');
      this.token = data.token; this.alunoId = data.alunoId;
      localStorage.setItem('gymdist_aluno_jwt', this.token);
      localStorage.setItem('gymdist_aluno_id', this.alunoId);
      document.getElementById('form-login-aluno').reset();
      this.mostrarToast('Login realizado com sucesso.', 'success');
      await this.carregarDadosDoAluno();
    } catch (error) {
      alerta.textContent = error.message; alerta.classList.remove('d-none');
    } finally { botao.disabled = false; }
  }

  logout(confirmar = true) {
    const alteracoes = Array.from(document.querySelectorAll('.input-carga-real, .chk-concluido')).some(campo =>
      campo.type === 'checkbox' ? campo.checked !== campo.defaultChecked : campo.value !== campo.defaultValue);
    if (confirmar && alteracoes && !window.confirm('Há alterações no treino não salvas. Deseja descartá-las e sair do portal?')) return false;
    this.token = null;
    this.alunoId = null;
    this.alunoDados = null;
    localStorage.removeItem('gymdist_aluno_jwt');
    localStorage.removeItem('gymdist_aluno_id');
    window.location.reload();
    return true;
  }

  // ─── Carregamento de Dados do Aluno ──────────────────────────────────────

  async carregarDadosDoAluno() {
    if (!this.alunoId) return;

    try {
      const resp = await this.apiRequest(`/api/buscarAluno/${this.alunoId}`);
      if (resp.ok) {
        this.alunoDados = await resp.json();
        this.renderizarCabecalhoEPerfil();
        this.mostrarPortalAluno();

        // Carrega fichas e mensalidades do aluno
        await Promise.all([
          this.carregarMeusTreinos(),
          this.carregarMinhasMensalidades()
        ]);
      } else {
        this.logout(false);
      }
    } catch (e) {
      console.error('Erro ao buscar dados do aluno:', e);
    }
  }

  renderizarCabecalhoEPerfil() {
    if (!this.alunoDados) return;

    const nome = this.alunoDados.nome;
    const primeiraLetra = nome ? nome.charAt(0).toUpperCase() : 'A';

    // Header
    document.getElementById('header-nome-aluno').textContent = nome;
    document.getElementById('avatar-letra').textContent = primeiraLetra;

    const statusBadge = this.alunoDados.status === 'ATIVO'
      ? '<span class="badge badge-success">Ativo</span>'
      : this.alunoDados.status === 'INADIMPLENTE'
        ? '<span class="badge badge-warning">Inadimplente</span>'
        : '<span class="badge badge-danger">Inativo</span>';

    document.getElementById('header-status-aluno').outerHTML = `<span id="header-status-aluno">${statusBadge}</span>`;

    // Perfil
    document.getElementById('perfil-nome').textContent = nome;
    document.getElementById('perfil-cpf').textContent = this.alunoDados.cpf || '—';
    document.getElementById('perfil-email').textContent = this.alunoDados.email || '—';
    document.getElementById('perfil-telefone').textContent = this.alunoDados.telefone || '—';
    document.getElementById('perfil-status').innerHTML = statusBadge;

    const biometria = (this.alunoDados.peso ? `${this.alunoDados.peso} kg` : 'Não informado') + 
                      ' / ' + 
                      (this.alunoDados.altura ? `${this.alunoDados.altura} m` : 'Não informada');
    document.getElementById('perfil-biometria').textContent = biometria;
  }

  // ─── Fichas de Treino do Aluno ───────────────────────────────────────────

  async carregarMeusTreinos() {
    const container = document.getElementById('fichas-aluno-container');
    if (!container || !this.alunoId) return;

    container.innerHTML = '<p class="text-muted text-center py-4">⏳ Carregando suas fichas de treino...</p>';

    try {
      await this.atualizarMatricula();
      const resp = await this.apiRequest(`/api/listarFichasAluno/${this.alunoId}`);
      if (resp.ok) {
        const fichas = await resp.json();
        this.renderizarFichas(fichas, container);
      } else {
        const err = await resp.json().catch(() => ({ mensagem: 'Falha ao buscar treinos.' }));
        container.innerHTML = `
          <div class="alert alert-danger mt-3">
            <span class="alert-icon">⚠️</span>
            <div class="alert-content">
              <strong>Não foi possível carregar suas fichas</strong>
              <p>${err.mensagem || 'Erro na resposta do servidor'}</p>
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
            <p>${e.message}</p>
          </div>
        </div>
      `;
    }
  }

  async atualizarMatricula() {
    const resp = await this.apiRequest(`/api/buscarAluno/${this.alunoId}`);
    if (!resp.ok) throw new Error('Não foi possível conferir a situação da matrícula. Recarregue e tente novamente.');
    this.alunoDados = await resp.json();
    this.renderizarCabecalhoEPerfil();
  }

  renderizarFichas(fichas, container) {
    const inativo = this.alunoDados?.status === 'INATIVO';
    if (!fichas || fichas.length === 0) {
      container.innerHTML = `
        <div class="text-center py-5">
          <p class="text-muted mb-2">Você ainda não possui fichas de treino prescritas.</p>
          <small class="text-muted d-block">Solicite ao seu instrutor para cadastrar ou gerar uma ficha com IA.</small>
        </div>
      `;
      return;
    }

    container.innerHTML = fichas.map(f => {
      const badgeStatus = f.status === 'CONCLUIDO'
        ? '<span class="badge badge-success">Concluído</span>'
        : f.itens.some(item => item.concluido)
          ? '<span class="badge badge-warning">Em andamento</span>'
          : '<span class="badge badge-warning">Pendente</span>';

      const itensHtml = f.itens.map(item => `
        <div class="exercicio-item-card" data-item-id="${item.id}">
          <div>
            <strong>${this.escaparTexto(item.nomeExercicio)}</strong><br>
            <small class="text-muted">${this.escaparTexto(item.grupoMuscular || 'Geral')}</small>
          </div>
          <div>${item.series}x${item.repeticoes}</div>
          <div title="Carga sugerida pelo instrutor">Sug.: ${item.cargaSugerida}kg</div>
          <div>
            <input type="number" step="0.5" min="0" max="1000" class="form-control form-control-sm input-carga-real"
                   value="${item.cargaUsada > 0 ? item.cargaUsada : item.cargaSugerida}"
                   placeholder="Peso usado (kg)" ${inativo ? 'disabled' : ''}>
          </div>
          <div>
            <label style="display:flex; align-items:center; gap:0.4rem; cursor:pointer;">
              <input type="checkbox" class="chk-concluido" ${inativo ? 'disabled' : ''} ${item.concluido ? 'checked' : ''}>
              <span class="text-xs">Feito</span>
            </label>
          </div>
        </div>
      `).join('');

      return `
        <div class="ficha-card" id="card-ficha-${f.id}">
          <div class="ficha-header">
            <div>
              <strong>${this.escaparTexto(f.titulo)}</strong>
              <small class="text-muted d-block">Objetivo: ${this.escaparTexto(f.objetivo)} · Criada em: ${f.dataCriacao || 'Recente'}</small>
            </div>
            ${badgeStatus}
          </div>

          <div class="exercicios-lista mt-3">
            <div class="exercicio-item-card" style="font-weight:600; font-size:0.75rem; color:var(--text-muted); background:transparent; border:none; padding:0.2rem 0.6rem;">
              <div>EXERCÍCIO</div>
              <div>SÉRIES</div>
              <div>SUGERIDO</div>
              <div>PESO REAL (KG)</div>
              <div>CONCLUÍDO</div>
            </div>
            ${itensHtml}
          </div>

          <div class="ficha-footer">
            <small class="text-muted">${inativo ? 'Matrícula inativa: histórico disponível apenas para consulta.' : 'Ajuste os pesos usados hoje e marque a execução do treino.'}</small>
            <button class="btn btn-sm btn-primary" onclick="alunoApp.registrarExecucaoTreino('${f.id}')" ${inativo ? 'disabled' : ''}>
              ${inativo ? 'Registro bloqueado' : 'Salvar Execução do Treino'}
            </button>
          </div>
        </div>
      `;
    }).join('');
  }

  async registrarExecucaoTreino(fichaId) {
    const card = document.getElementById(`card-ficha-${fichaId}`);
    if (!card) return;

    const rows = card.querySelectorAll('.exercicio-item-card[data-item-id]');
    const itens = [];

    for (const row of rows) {
      const valor = row.querySelector('.input-carga-real')?.value;
      const cargaUsada = Number(valor);
      if (valor === '' || !Number.isFinite(cargaUsada) || cargaUsada < 0 || cargaUsada > 1000) {
        this.mostrarToast('Informe a carga usada em kg, entre 0 e 1000. Não use valores negativos.', 'warning');
        return;
      }
      itens.push({ itemId:row.dataset.itemId, cargaUsada, concluido:row.querySelector('.chk-concluido')?.checked || false });
    }

    try {
      await this.atualizarMatricula();
      if (this.alunoDados.status === 'INATIVO') {
        this.mostrarToast('Matrícula inativa. O registro de execução está bloqueado. Procure a administração da academia.', 'warning');
        await this.carregarMeusTreinos();
        return;
      }
      const resp = await this.apiRequest(`/api/registrarExecucaoTreino/${fichaId}`, {
        method: 'POST',
        body: JSON.stringify({ itens })
      });

      if (resp.ok) {
        const fichaAtualizada = await resp.json();
        this.mostrarToast(fichaAtualizada.status === 'CONCLUIDO'
          ? 'Treino concluído! Todos os exercícios foram feitos.'
          : 'Progresso salvo. O treino continua pendente de conclusão.', 'success');
        await this.carregarMeusTreinos();
      } else {
        const err = await resp.json().catch(() => ({ mensagem: 'Falha ao registrar execução.' }));
        this.mostrarToast(err.mensagem || 'Erro ao registrar execução.', 'danger');
      }
    } catch (e) {
      this.mostrarToast(`Falha de comunicação: ${e.message}`, 'danger');
    }
  }

  // ─── Mensalidades do Aluno ───────────────────────────────────────────────

  async carregarMinhasMensalidades() {
    const tbody = document.getElementById('tbody-minhas-mensalidades');
    if (!tbody || !this.alunoId) return;

    tbody.innerHTML = '<tr><td colspan="6" class="text-center text-muted py-4">⏳ Carregando mensalidades...</td></tr>';

    try {
      const resp = await this.apiRequest(`/api/listarMensalidadesAluno/${this.alunoId}`);
      if (resp.ok) {
        const mensalidades = await resp.json();
        this.renderizarMensalidades(mensalidades, tbody);
      } else {
        const err = await resp.json().catch(() => ({}));
        this.mostrarErroMensalidades(tbody, err.mensagem || `Não foi possível carregar as mensalidades (HTTP ${resp.status}).`);
      }
    } catch (e) {
      this.mostrarErroMensalidades(tbody, 'Falha de conexão. Tente recarregar as mensalidades.');
    }
  }

  mostrarErroMensalidades(tbody, mensagem) {
    tbody.replaceChildren();
    const linha = tbody.insertRow();
    const celula = linha.insertCell();
    celula.colSpan = 6;
    celula.className = 'text-center py-4';
    const texto = document.createElement('p');
    texto.textContent = mensagem;
    const botao = document.createElement('button');
    botao.type = 'button';
    botao.className = 'btn btn-sm btn-outline mt-2';
    botao.textContent = 'Tentar novamente';
    botao.addEventListener('click', () => this.carregarMinhasMensalidades());
    celula.append(texto, botao);
  }

  formatarData(valor, incluirHora = false) {
    if (!valor) return '—';
    const partes = /^(\d{4})-(\d{2})-(\d{2})(?:T(\d{2}):(\d{2}))?/.exec(valor);
    if (!partes) return '—';
    return `${partes[3]}/${partes[2]}/${partes[1]}` + (incluirHora && partes[4] ? ` às ${partes[4]}:${partes[5]}` : '');
  }

  escaparTexto(valor) {
    const texto = document.createElement('span');
    texto.textContent = valor;
    return texto.innerHTML;
  }

  renderizarMensalidades(mensalidades, tbody) {
    if (!mensalidades || mensalidades.length === 0) {
      tbody.innerHTML = '<tr><td colspan="6" class="text-center text-muted py-4">Você não possui faturas ou mensalidades registradas.</td></tr>';
      return;
    }

    tbody.innerHTML = mensalidades.map(m => {
      const statusBadge = m.status === 'PAGO'
        ? '<span class="badge badge-success">Pago</span>'
        : m.status === 'ATRASADO'
          ? '<span class="badge badge-danger">Atrasado</span>'
          : '<span class="badge badge-warning">Pendente</span>';

      const valorFmt = Number(m.valor).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
      return `
        <tr>
          <td><strong>${this.escaparTexto(m.descricao || 'Mensalidade')}</strong></td>
          <td><strong>${valorFmt}</strong></td>
          <td>${this.formatarData(m.dataVencimento)}</td>
          <td>${statusBadge}</td>
          <td>${this.formatarData(m.dataPagamento, true)}</td>
          <td>${this.escaparTexto(m.formaPagamento || '—')}</td>
        </tr>
      `;
    }).join('');
  }

  // ─── Navegação e UI Helpers ──────────────────────────────────────────────

  navegarParaAba(tabId) {
    document.querySelectorAll('.nav-tab').forEach(b => b.classList.remove('active'));
    document.querySelectorAll('.tab-pane').forEach(p => p.classList.remove('active'));

    const tabBtn = document.querySelector(`.nav-tab[data-tab="${tabId}"]`);
    const tabPane = document.getElementById(tabId);

    if (tabBtn) tabBtn.classList.add('active');
    if (tabPane) tabPane.classList.add('active');
  }

  abrirModal(modalId) {
    const el = document.getElementById(modalId);
    if (el) el.classList.add('active');
  }

  fecharModal(modalId) {
    const el = document.getElementById(modalId);
    if (el) el.classList.remove('active');
  }

  mostrarToast(mensagem, tipo = 'info', duracao = 4500) {
    const container = document.getElementById('toast-container');
    if (!container) return;

    const toast = document.createElement('div');
    toast.className = `toast toast-${tipo}`;

    const icones = {
      success: '✓',
      danger: '✕',
      warning: '⚠️',
      info: 'ℹ️'
    };

    const mensagemEl = document.createElement('span'); mensagemEl.textContent = mensagem;
    const fechar = document.createElement('button'); fechar.className = 'toast-close'; fechar.textContent = '×';
    toast.append(mensagemEl, fechar);

    toast.querySelector('.toast-close').addEventListener('click', () => {
      toast.remove();
    });

    container.appendChild(toast);

    setTimeout(() => {
      toast.style.opacity = '0';
      toast.style.transform = 'translateY(10px)';
      setTimeout(() => toast.remove(), 300);
    }, duracao);
  }

  setupEventListeners() {
    window.addEventListener('unhandledrejection', event => {
      event.preventDefault(); this.mostrarToast('Não foi possível concluir a operação. Tente novamente em instantes.', 'danger');
    });
    // Abas
    document.querySelectorAll('.nav-tab').forEach(tab => {
      tab.addEventListener('click', () => this.navegarParaAba(tab.dataset.tab));
    });

    // Login Form
    document.getElementById('form-login-aluno')?.addEventListener('submit', (e) => {
      e.preventDefault();
      this.login(document.getElementById('login-email-aluno').value, document.getElementById('login-senha-aluno').value);
    });

    // Logout
    document.getElementById('btn-aluno-logout')?.addEventListener('click', () => {
      this.logout();
    });

    // Recarregar Treinos
    document.getElementById('btn-atualizar-meus-treinos')?.addEventListener('click', () => {
      this.carregarMeusTreinos();
      this.mostrarToast('Treinos atualizados.', 'info');
    });

    // Recarregar Mensalidades
    document.getElementById('btn-atualizar-minhas-mensalidades')?.addEventListener('click', () => {
      this.carregarMinhasMensalidades();
      this.mostrarToast('Mensalidades atualizadas.', 'info');
    });


  }
}

// Inicialização
document.addEventListener('DOMContentLoaded', () => {
  window.alunoApp = new AlunoApp();
});
