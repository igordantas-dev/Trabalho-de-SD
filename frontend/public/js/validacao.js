// Mensagens independentes do idioma do navegador, inclusive em campos dinamicos.
window.GymValidacao = {
  mensagem(campo) {
    const estado = campo.validity;
    const numero = valor => {
      if (valor === '') return valor;
      const n = Number(valor);
      return Number.isFinite(n) ? n.toLocaleString('pt-BR', { maximumFractionDigits: 10 }) : valor;
    };
    if (estado.valueMissing) return campo.tagName === 'SELECT'
      ? 'Selecione uma opção.' : 'Preencha este campo.';
    if (estado.badInput) return campo.type === 'number'
      ? 'Informe um número válido.' : 'Informe um valor válido para este campo.';
    if (estado.typeMismatch) return campo.type === 'email'
      ? 'Informe um e-mail válido. Exemplo: nome@exemplo.com.' : 'Informe um endereço válido.';
    if (campo.id === 'aluno-altura' && (estado.rangeUnderflow || estado.rangeOverflow))
      return 'Informe a altura em metros, entre 0,50 e 2,50. Para 170 cm, use 1,70.';
    if (estado.rangeUnderflow) return `Informe um valor maior ou igual a ${numero(campo.min)}.`;
    if (estado.rangeOverflow) return `Informe um valor menor ou igual a ${numero(campo.max)}.`;
    if (estado.tooShort) return `Use pelo menos ${campo.minLength} caracteres.`;
    if (estado.tooLong) return `Use no máximo ${campo.maxLength} caracteres.`;
    if (estado.patternMismatch) return 'Confira o formato informado neste campo.';
    if (estado.stepMismatch) return campo.id === 'aluno-altura'
      ? 'Informe a altura com até duas casas decimais. Exemplo: 1,70.'
      : `Informe um valor válido em incrementos de ${numero(campo.step)}.`;
    if (!estado.valid) return 'Confira o valor informado neste campo.';
    return '';
  },
  validar(campo) {
    if (!campo || typeof campo.setCustomValidity !== 'function') return;
    campo.setCustomValidity('');
    if (campo.willValidate) campo.setCustomValidity(this.mensagem(campo));
  }
};

// invalid nao propaga; captura tambem alcança campos adicionados posteriormente.
for (const evento of ['invalid', 'input', 'change']) {
  document.addEventListener(evento, event => window.GymValidacao.validar(event.target), true);
}
document.addEventListener('reset', event => {
  for (const campo of event.target.elements || []) {
    if (typeof campo.setCustomValidity === 'function') campo.setCustomValidity('');
  }
}, true);
