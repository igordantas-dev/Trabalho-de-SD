#!/usr/bin/env python3
"""Consulta ou configura escala dos cinco servicos. Apresentacao: min=2, max=2."""
import argparse
import json
import subprocess

SERVICES = ('gymflow-frontend','gymflow-gateway','gymflow-aluno','gymflow-treino','gymflow-financeiro')
COMMON = ('--project=sd2026','--region=us-central1')

def status():
    print('Servico | min servico | max servico | min revisao | max revisao')
    for name in SERVICES:
        result = subprocess.run(['gcloud','run','services','describe',name,*COMMON,'--format=json'],check=True,capture_output=True,text=True)
        service = json.loads(result.stdout)
        service_values = service.get('metadata',{}).get('annotations',{})
        revision_values = service['spec']['template'].get('metadata',{}).get('annotations',{})
        print(' | '.join([name,service_values.get('run.googleapis.com/minScale','nao definido'),
          service_values.get('run.googleapis.com/maxScale','nao definido'),
          revision_values.get('autoscaling.knative.dev/minScale','nao definido'),
          revision_values.get('autoscaling.knative.dev/maxScale','nao definido')]))

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('modo',choices=('status','economia','apresentacao'),default='status',nargs='?')
    args = parser.parse_args()
    if args.modo != 'status':
        minimum = '2' if args.modo == 'apresentacao' else '0'
        for name in SERVICES:
            subprocess.run(['gcloud','run','services','update',name,*COMMON,
                '--scaling=auto','--min='+minimum,'--max=2',
                '--min-instances=0','--max-instances=2','--quiet'],check=True)
        print('ESCALA ATUALIZADA: minimo='+minimum+', maximo=2. Revisoes sem minimo proprio.')
    status()

if __name__ == '__main__': main()
