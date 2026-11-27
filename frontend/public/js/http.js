// Requisicoes com prazo e mensagens comuns, sem expor detalhes do servidor.
window.GymHttp = {
  async request(url, options = {}) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 30000);
    try {
      const response = await fetch(url, { ...options, signal: controller.signal });
      if (response.ok) return response;
      const data = await response.json().catch(() => ({}));
      if (!data.mensagem || response.status >= 500) {
        data.mensagem = response.status >= 500
          ? 'Não foi possível concluir esta operação agora. Tente novamente em instantes.'
          : 'Não foi possível concluir a operação. Verifique os dados e tente novamente.';
      }
      if (response.status === 401) data.mensagem = url === '/api/autenticar' ? 'E-mail ou senha incorretos.' : 'Sua sessão expirou. Entre novamente.';
      if (response.status === 403) data.mensagem = 'Você não tem permissão para realizar esta operação.';
      return new Response(JSON.stringify(data), { status: response.status, headers: { 'Content-Type': 'application/json' } });
    } catch (error) {
      throw new Error(error.name === 'AbortError' ? 'A operação demorou mais que o esperado. Tente novamente.' : 'Não foi possível conectar ao sistema. Verifique sua conexão e tente novamente.');
    } finally { clearTimeout(timer); }
  }
};

window.addEventListener('error', () => {
  const area = document.getElementById('toast-container');
  if (!area) return;
  const aviso = document.createElement('div');
  aviso.className = 'toast danger'; aviso.setAttribute('role', 'alert');
  aviso.textContent = 'Não foi possível concluir a operação. Recarregue a página e tente novamente.';
  area.appendChild(aviso); setTimeout(() => aviso.remove(), 6000);
});
