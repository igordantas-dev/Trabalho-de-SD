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

    // Carrega alunos para o select de identificação
    await this.carregarAlunosParaSelect();

    if (this.token && this.alunoId) {
      await this.carregarDadosDoAluno();
    } else {
      this.mostrarTelaLogin();
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
      const response = await fetch(endpoint, {
        ...options,
        headers
      });

      if (response.status === 401) {
        this.logout(false);
        this.mostrarToast('Sessão de demonstração expirada. Entre novamente.', 'warning');
      }

      return response;
    } catch (err) {
      this.mostrarToast(`Falha de rede: ${err.message}`, 'danger');
      throw err;
    }
  }

  // ─── Autenticação do Aluno ───────────────────────────────────────────────

  async carregarAlunosParaSelect() {
    const select = document.getElementById('login-aluno-select');
    if (!select) return;

    try {
      // Usamos chamada com token de busca inicial
      const tokenDemo = await (await fetch('/api/gerarToken')).json();
      const resp = await fetch('/api/listarAlunos', {
        headers: {
          'Authorization': `Bearer ${tokenDemo.token}`,
          'Content-Type': 'application/json'
        }
      });

      if (resp.ok) {
        this.alunosDisponiveis = await resp.json();
        if (this.alunosDisponiveis.length === 0) {
          select.innerHTML = '<option value="">Nenhum aluno cadastrado no sistema</option>';
        } else {
          select.innerHTML = '<option value="">-- Selecione seu nome --</option>' +
            this.alunosDisponiveis.map(a => `<option value="${a.id}">${a.nome} (${a.cpf})</option>`).join('');
          
          if (this.alunoId) {
            select.value = this.alunoId;
          }
        }
      }
    } catch (e) {
      select.innerHTML = '<option value="">Erro ao carregar lista de alunos</option>';
    }
  }

  async login(alunoId) {
    const botao = document.getElementById('btn-entrar-aluno');
    if (botao.disabled) return;
    botao.disabled = true;
    try {
      // Autentica via JWT
      const resp = await fetch('/api/gerarToken');

      if (!resp.ok) {
        this.mostrarToast('Não foi possível iniciar a demonstração.', 'danger');
        return;
      }

      const data = await resp.json();
      if (!data.token) throw new Error('Não foi possível iniciar a demonstração.');
      this.token = data.token;
      this.alunoId = alunoId;

      localStorage.setItem('gymdist_aluno_jwt', this.token);
      localStorage.setItem('gymdist_aluno_id', this.alunoId);

      this.mostrarToast('Demonstração do portal iniciada.', 'success');
      await this.carregarDadosDoAluno();
    } catch (e) {
      this.mostrarToast(`Erro ao conectar: ${e.message}`, 'danger');
    } finally {
      botao.disabled = false;
    }
  }

  logout(confirmar = true) {
    const alteracoes = Array.from(document.querySelectorAll('.input-carga-real, .chk-concluido')).some(campo =>
      campo.type === 'checkbox' ? campo.checked !== campo.defaultChecked : campo.value !== campo.defaultValue);
    if (confirmar && alteracoes && !window.confirm('Há alterações no treino não salvas. Deseja descartá-las e sair da demonstração?')) return false;
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
            <strong>${item.nomeExercicio}</strong><br>
            <small class="text-muted">${item.grupoMuscular || 'Geral'}</small>
          </div>
          <div>${item.series}x${item.repeticoes}</div>
          <div title="Carga sugerida pelo instrutor">Sug.: ${item.cargaSugerida}kg</div>
          <div>
            <input type="number" step="0.5" class="form-control form-control-sm input-carga-real"
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
              <strong>${f.titulo}</strong>
              <small class="text-muted d-block">Objetivo: ${f.objetivo} · Criada em: ${f.dataCriacao || 'Recente'}</small>
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

    rows.forEach(row => {
      const itemId = row.dataset.itemId;
      const cargaUsada = parseFloat(row.querySelector('.input-carga-real')?.value) || 0;
      const concluido = row.querySelector('.chk-concluido')?.checked || false;

      itens.push({
        itemId,
        cargaUsada,
        concluido
      });
    });

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

    toast.innerHTML = `
      <div class="toast-icon">${icones[tipo] || '•'}</div>
      <div class="toast-content">
        <div class="toast-message">${mensagem}</div>
      </div>
      <button class="toast-close">&times;</button>
    `;

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
    // Abas
    document.querySelectorAll('.nav-tab').forEach(tab => {
      tab.addEventListener('click', () => this.navegarParaAba(tab.dataset.tab));
    });

    // Login Form
    document.getElementById('form-login-aluno')?.addEventListener('submit', (e) => {
      e.preventDefault();
      const alunoId = document.getElementById('login-aluno-select').value;

      if (!alunoId) {
        this.mostrarToast('Selecione seu nome de aluno.', 'warning');
        return;
      }
      this.login(alunoId);
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
