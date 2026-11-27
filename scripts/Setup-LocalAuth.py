#!/usr/bin/env python3
"""Cria credenciais locais; nao imprime ou envia senhas, hashes ou tokens."""
import base64
import getpass
import hashlib
import os
from pathlib import Path
import secrets

def password_hash(password):
    salt = secrets.token_bytes(16)
    encoded = hashlib.pbkdf2_hmac('sha256', password.encode('utf-8'), salt, 600000)
    return 'pbkdf2_sha256$600000$' + base64.b64encode(salt).decode() + '$' + base64.b64encode(encoded).decode()

def main():
    root = Path(__file__).resolve().parent.parent
    target = root / '.env.auth'
    if target.exists():
        raise SystemExit('Credenciais ja existem em .env.auth. Remova esse arquivo manualmente apenas se desejar redefinir o acesso local.')
    login = input('E-mail da gestao: ').strip().lower()
    if '@' not in login or any(c in login for c in '\r\n='):
        raise SystemExit('Informe um e-mail valido.')
    password = getpass.getpass('Senha da gestao (8 a 128 caracteres): ')
    if not 8 <= len(password) <= 128:
        raise SystemExit('A senha deve ter entre 8 e 128 caracteres.')
    if password != getpass.getpass('Confirme a senha: '):
        raise SystemExit('As senhas nao coincidem.')
    # Compose env_file raw evita interpretar os caracteres $ do hash.
    content = 'ADMIN_LOGIN=' + login + '\nADMIN_PASSWORD_HASH=' + password_hash(password) + '\nJWT_SECRET=' + secrets.token_hex(32) + '\n'
    with target.open('x', encoding='utf-8', newline='\n') as stream:
        stream.write(content)
    try: os.chmod(target, 0o600)
    except OSError: pass
    print('Acesso local configurado. Arquivo privado .env.auth criado; nao envie ao GitHub.')

if __name__ == '__main__': main()
