#!/usr/bin/env python3
"""Cloud Shell demo: loss of Gateway -> Financeiro connectivity, with restoration."""
import argparse
import json
import getpass
from pathlib import Path
import re
import subprocess
import sys
import time
from urllib.error import HTTPError
from urllib.request import Request, urlopen

PROJECT = "sd2026"
REGION = "us-central1"
GATEWAY = "gymflow-gateway"


def gcloud(args, capture=False):
    result = subprocess.run(
        ["gcloud", *args], check=True, text=True,
        stdout=subprocess.PIPE if capture else None,
    )
    return json.loads(result.stdout) if capture else None


def evidence_group(entries):
    """Require all three attempts for one call, in the selected revision."""
    groups = {}
    for entry in entries:
        text = entry.get("textPayload", "")
        if "academia.proto.FinanceiroService/ListarMensalidadesPorAluno" not in text:
            continue
        if "transparent_retry=" not in text:
            continue
        match = re.search(r"grpc_call=(\d+).*?attempt=(\d+)", text)
        if match:
            groups.setdefault(match[1], {}).setdefault(int(match[2]), text)
    for attempts in groups.values():
        if {1, 2, 3}.issubset(attempts):
            return [attempts[number] for number in (1, 2, 3)]
    return None


class Demo:
    def __init__(self, recovery_file=None):
        self.recovery_file = Path(recovery_file) if recovery_file else (
            Path.home() / "gymflow-financeiro-restore.json"
        )
        self.base_url = None
        self.token = None
        self.credentials = None

    def service(self, name):
        return gcloud([
            "run", "services", "describe", name,
            f"--project={PROJECT}", f"--region={REGION}", "--format=json",
        ], capture=True)

    def endpoint(self, host, port):
        gcloud([
            "run", "services", "update", GATEWAY,
            f"--project={PROJECT}", f"--region={REGION}",
            f"--update-env-vars=FINANCEIRO_HOST={host},FINANCEIRO_PORT={port}",
            "--quiet",
        ])

    def request(self, route, data=None):
        headers = {"Authorization": f"Bearer {self.token}"} if self.token else {}
        if data is not None: headers['Content-Type'] = 'application/json'
        request = Request(f"{self.base_url}/api/{route}", headers=headers,
            data=json.dumps(data).encode() if data is not None else None)
        try:
            with urlopen(request, timeout=60) as response:
                return response.status, json.load(response)
        except HTTPError as error:
            # Do not print bodies: they may contain user data.
            return error.code, None

    def expect(self, route, code, payload=None):
        actual, data = self.request(route, payload)
        if actual != code:
            raise RuntimeError(f"{route.split('/')[0]}: esperado HTTP {code}, recebido {actual}")
        return data

    def restore(self):
        snapshot = json.loads(self.recovery_file.read_text(encoding="utf-8"))
        if (snapshot.get("project"), snapshot.get("region"), snapshot.get("gateway")) != (
                PROJECT, REGION, GATEWAY):
            raise RuntimeError("Arquivo de restauracao pertence a outro servico/projeto.")
        print("Restaurando o endereco original do financeiro...", flush=True)
        self.endpoint(snapshot["host"], snapshot["port"])
        self.recovery_file.unlink()
        print("[OK] Endereco original restaurado", flush=True)

    def retry_logs(self, revision):
        query = (
            'resource.type="cloud_run_revision" '
            f'AND resource.labels.service_name="{GATEWAY}" '
            f'AND resource.labels.revision_name="{revision}" '
            'AND textPayload:"academia.proto.FinanceiroService/ListarMensalidadesPorAluno"'
        )
        for _ in range(8):
            entries = gcloud([
                "logging", "read", query, f"--project={PROJECT}",
                "--freshness=10m", "--order=asc", "--limit=100", "--format=json",
            ], capture=True)
            evidence = evidence_group(entries)
            if evidence:
                for line in evidence:
                    print(line, flush=True)
                print("[OK] Cloud Logging confirmou tentativas 1, 2 e 3 da mesma chamada", flush=True)
                print("Filtro para abrir no Logs Explorer:\n" + query, flush=True)
                return
            time.sleep(3)
        raise RuntimeError("Logs ainda nao comprovaram as tres tentativas da mesma chamada.")

    def run(self):
        if self.recovery_file.exists():
            raise RuntimeError(
                "Ha uma restauracao pendente. Execute: "
                "python3 scripts/Test-Resiliencia-Gcp.py --restore"
            )
        config = self.service(GATEWAY)
        conditions = config.get("status", {}).get("conditions", [])
        if not any(item.get("type") == "Ready" and item.get("status") == "True"
                   for item in conditions):
            raise RuntimeError("Gateway nao esta pronto; nao iniciaremos a simulacao.")
        traffic = config["status"].get("traffic", [])
        if len(traffic) != 1 or traffic[0].get("percent") != 100:
            raise RuntimeError("O teste exige uma unica revisao recebendo 100% do trafego.")
        container = config["spec"]["template"]["spec"]["containers"][0]
        env = {item["name"]: item.get("value") for item in container.get("env", [])}
        host, port = env.get("FINANCEIRO_HOST"), env.get("FINANCEIRO_PORT")
        if not host or host in ("127.0.0.1", "localhost") or not port:
            raise RuntimeError("Endereco original do financeiro ausente ou ja alterado.")
        self.base_url = self.service("gymflow-frontend")["status"]["url"].rstrip("/")
        if self.credentials is None:
            self.credentials = (input('E-mail da gestao: ').strip(), getpass.getpass('Senha da gestao: '))
        email, password = self.credentials
        try:
            auth = self.expect("autenticar", 200, {"username":email,"senha":password,"perfil":"ADMIN"})
        finally:
            self.credentials = None
            password = None
        if not isinstance(auth, dict) or not auth.get("token"):
            raise RuntimeError("Token nao foi retornado.")
        self.token = auth["token"]
        alunos = self.expect("listarAlunos", 200)
        if not alunos:
            raise RuntimeError("Cadastre um aluno pelo frontend antes do teste.")
        route = f"listarMensalidadesAluno/{alunos[0]['id']}"
        self.expect(route, 200)
        print("[OK] Alunos e financeiro disponiveis pela URL HTTPS do frontend", flush=True)

        snapshot = dict(project=PROJECT, region=REGION, gateway=GATEWAY, host=host, port=port)
        self.recovery_file.write_text(json.dumps(snapshot), encoding="utf-8")
        print(f"Restauracao de emergencia: {self.recovery_file}", flush=True)
        try:
            print("Simulando perda de conectividade com o financeiro...", flush=True)
            # Local loopback port 1 has no service; failure is immediate UNAVAILABLE,
            # allowing retries to be observed instead of waiting for a network timeout.
            self.endpoint("127.0.0.1", "1")
            revision = self.service(GATEWAY)["status"]["latestReadyRevisionName"]
            self.expect(route, 503)
            print("[OK] Financeiro inacessivel retornou HTTP 503 tratado", flush=True)
            self.expect("listarAlunos", 200)
            print("[OK] Alunos continuam funcionando durante a falha", flush=True)
            self.retry_logs(revision)
        finally:
            # Snapshot survives Cloud Shell reconnects if this process is interrupted.
            self.restore()

        self.expect(route, 200)
        print("[OK] Financeiro voltou a responder HTTP 200", flush=True)
        print("TESTE DE RESILIENCIA NO GCP CONCLUIDO", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--restore", action="store_true", help="Restore a pending original endpoint")
    args = parser.parse_args()
    demo = Demo()
    try:
        if args.restore:
            demo.restore()
        else:
            demo.run()
    except (Exception, KeyboardInterrupt) as error:
        print(f"[FALHOU] {error}", file=sys.stderr, flush=True)
        if demo.recovery_file.exists():
            print(
                "Para restaurar: python3 scripts/Test-Resiliencia-Gcp.py --restore",
                file=sys.stderr, flush=True,
            )
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
