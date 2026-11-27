#!/usr/bin/env python3
"""Prepara credencial administrativa ou publica as imagens ja construidas pelo Cloud Build."""
import argparse
import getpass
import json
from pathlib import Path
import subprocess
import sys
from urllib.error import HTTPError
from urllib.parse import urlparse
from urllib.request import Request, urlopen
from importlib.util import spec_from_file_location, module_from_spec

PROJECT = 'sd2026'
REGION = 'us-central1'
SECRET = 'gymflow-admin-password-hash'
COMMON = ['--project='+PROJECT, '--region='+REGION]
ROOT = Path(__file__).resolve().parent.parent
CONFIG = Path.home() / 'gymflow-auth-deploy.json'
BACKENDS = ('gymflow-aluno','gymflow-treino','gymflow-financeiro')

def call(args, data=None, capture=True):
    result = subprocess.run(['gcloud', *args], check=True, text=True, input=data,
        stdout=subprocess.PIPE if capture else None)
    return result.stdout if capture else None

def service(name):
    return json.loads(call(['run','services','describe',name,*COMMON,'--format=json']))

def prepare():
    spec = spec_from_file_location('local_auth', ROOT/'scripts'/'Setup-LocalAuth.py')
    module = module_from_spec(spec); spec.loader.exec_module(module)
    email = input('E-mail da gestao: ').strip().lower()
    if '@' not in email or any(c in email for c in '\r\n,='):
        raise ValueError('E-mail invalido.')
    senha = getpass.getpass('Senha da gestao (8 a 128 caracteres): ')
    if not 8 <= len(senha) <= 128 or senha != getpass.getpass('Confirme a senha: '):
        raise ValueError('Senha invalida ou confirmacao diferente.')
    hash_value = module.password_hash(senha)
    senha = None
    existing = subprocess.run(['gcloud','secrets','describe',SECRET,'--project='+PROJECT,'--format=json'],capture_output=True,text=True)
    if existing.returncode:
        if 'NOT_FOUND' not in existing.stderr: raise RuntimeError('Nao foi possivel consultar o secret. Confira suas permissoes.')
        call(['secrets','create',SECRET,'--project='+PROJECT,'--replication-policy=automatic','--quiet'])
    version = json.loads(call(['secrets','versions','add',SECRET,'--project='+PROJECT,'--data-file=-','--format=json'],hash_value))['name'].rsplit('/',1)[1]
    hash_value = None
    identity = service('gymflow-gateway')['spec']['template']['spec']['serviceAccountName']
    call(['secrets','add-iam-policy-binding',SECRET,'--project='+PROJECT,
        '--member=serviceAccount:'+identity,'--role=roles/secretmanager.secretAccessor','--quiet'])
    CONFIG.write_text(json.dumps({'email':email,'version':version,'identity':identity}),encoding='utf-8')
    print('CREDENCIAL ADMINISTRATIVA PREPARADA. Senha nao gravada no repositorio nem exibida.')

def api(base,route,token=None,payload=None):
    headers={'Content-Type':'application/json'}
    if token: headers['Authorization']='Bearer '+token
    request=Request(base+'/api/'+route,headers=headers,data=json.dumps(payload).encode() if payload is not None else None)
    try:
        with urlopen(request,timeout=60) as response:return response.status,json.load(response)
    except HTTPError as error:return error.code,None

def deploy(tag):
    import re
    if not re.fullmatch('[0-9a-f]{7,40}',tag): raise ValueError('Informe a tag do commit construido.')
    config=json.loads(CONFIG.read_text(encoding='utf-8'))
    for name in BACKENDS:
        call(['run','services','add-iam-policy-binding',name,*COMMON,
            '--member=serviceAccount:'+config['identity'],'--role=roles/run.invoker','--quiet'])
    registry='us-central1-docker.pkg.dev/'+PROJECT+'/gymflow/'
    call(['run','services','update','gymflow-aluno',*COMMON,'--image='+registry+'aluno-service:'+tag,'--quiet'],capture=False)
    hosts={name:urlparse(service(name)['status']['url']).hostname for name in BACKENDS}
    variables={'ADMIN_LOGIN':config['email'],'GRPC_IAM':'true','GRPC_TLS':'true','GRPC_DEADLINE_SECONDS':'20',
        'ALUNO_HOST':hosts['gymflow-aluno'],'TREINO_HOST':hosts['gymflow-treino'],'FINANCEIRO_HOST':hosts['gymflow-financeiro'],
        'ALUNO_PORT':'443','TREINO_PORT':'443','FINANCEIRO_PORT':'443'}
    call(['run','services','update','gymflow-gateway',*COMMON,'--image='+registry+'api-gateway:'+tag,
        '--update-env-vars='+','.join(k+'='+v for k,v in variables.items()),
        '--update-secrets=ADMIN_PASSWORD_HASH='+SECRET+':'+config['version'],'--quiet'],capture=False)
    call(['run','services','update','gymflow-frontend',*COMMON,'--image='+registry+'frontend:'+tag,'--quiet'],capture=False)
    base=service('gymflow-frontend')['status']['url']
    senha=getpass.getpass('Confirme a senha da gestao para validar a publicacao: ')
    status,data=api(base,'autenticar',payload={'username':config['email'],'senha':senha,'perfil':'ADMIN'})
    senha=None
    if status!=200 or not data or not data.get('token'):raise RuntimeError('Login nao validado. Confira a senha ou os logs antes de continuar.')
    token=data['token']
    if api(base,'listarAlunos',token)[0]!=200:raise RuntimeError('Consulta de alunos falhou. Confira a migracao e os logs.')
    # Fechar o acesso direto evita contornar as permissoes aplicadas pelo Gateway.
    for name in BACKENDS:
        policy=json.loads(call(['run','services','get-iam-policy',name,*COMMON,'--format=json']))
        for binding in policy.get('bindings',[]):
            if binding.get('role')=='roles/run.invoker':
                for principal in ('allUsers','allAuthenticatedUsers'):
                    if principal in binding.get('members',[]):
                        call(['run','services','remove-iam-policy-binding',name,*COMMON,
                            '--member='+principal,'--role=roles/run.invoker','--quiet'])
        call(['run','services','update',name,*COMMON,'--invoker-iam-check','--quiet'],capture=False)
    if api(base,'listarAlunos',token)[0]!=200:raise RuntimeError('IAM entre Gateway e alunos nao validado. Confira Cloud Logging.')
    token=None
    subprocess.run([sys.executable,str(ROOT/'scripts'/'Scaling-Gcp.py'),'economia'],check=True)
    print('LOGIN PUBLICADO E VALIDADO. Minimo 0, maximo 2. Defina senhas dos alunos existentes pela gestao.')

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('acao',choices=('prepare','deploy'))
    parser.add_argument('--tag')
    args=parser.parse_args()
    if args.acao=='prepare':prepare()
    elif args.tag:deploy(args.tag)
    else:parser.error('deploy exige --tag')

if __name__=='__main__':main()
