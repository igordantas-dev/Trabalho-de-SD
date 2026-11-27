#!/usr/bin/env python3
"""Run the versioned schema scripts through a private, temporary Auth Proxy."""
import argparse
import os
from pathlib import Path
import shutil
import socket
import subprocess
import sys
import tempfile
import time

INSTANCE = "sd2026:us-central1:gymflow-db"


def free_port():
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", 0))
        return listener.getsockname()[1]


def wait_proxy(proxy, port):
    deadline = time.monotonic() + 30
    while time.monotonic() < deadline:
        if proxy.poll() is not None:
            raise RuntimeError("Cloud SQL Auth Proxy encerrou durante a inicializacao.")
        try:
            with socket.create_connection(("127.0.0.1", port), timeout=1):
                return
        except OSError:
            time.sleep(0.25)
    raise RuntimeError("Cloud SQL Auth Proxy nao abriu a porta local em 30 segundos.")


def run_schema(mode):
    scripts = {
        "validate": "Validate.psql",
        "apply": "Apply.psql",
        "reset-data": "maintenance/Reset-cloud-data.psql",
    }
    if mode not in scripts:
        raise ValueError("Modo deve ser validate, apply ou reset-data.")
    for command in ("gcloud", "cloud-sql-proxy", "psql"):
        if not shutil.which(command):
            raise RuntimeError(f"Comando ausente no Cloud Shell: {command}")

    script = Path(__file__).resolve().parent.parent / "db" / scripts[mode]
    if not script.is_file():
        raise RuntimeError(f"Arquivo SQL ausente: {script}")

    result = subprocess.run([
        "gcloud", "secrets", "versions", "access", "1",
        "--secret=gymflow-db-password", "--project=sd2026",
    ], check=True, capture_output=True, text=True)
    password = result.stdout.rstrip("\r\n")
    if not password:
        raise RuntimeError("Secret da senha esta vazio.")

    port = free_port()
    env = os.environ.copy()
    env.update(PGPASSWORD=password, PGCONNECT_TIMEOUT="30")
    with tempfile.TemporaryFile(mode="w+t", encoding="utf-8") as log:
        proxy = subprocess.Popen([
            "cloud-sql-proxy", "--address=127.0.0.1", f"--port={port}", INSTANCE,
        ], stdout=log, stderr=subprocess.STDOUT)
        try:
            wait_proxy(proxy, port)
            # -w prohibits password prompts; PGPASSWORD is passed only to psql.
            if mode == "reset-data":
                print(f"Limpando registros somente no Cloud SQL: {INSTANCE}, banco academia.", flush=True)
            subprocess.run([
                "psql", "-X", "-w", "-h", "127.0.0.1", "-p", str(port),
                "-U", "gymflow_app", "-d", "academia",
                "-v", "ON_ERROR_STOP=1",
                "-v", "cloud_sql_instance=" + INSTANCE, "-f", str(script),
            ], env=env, check=True)
        except BaseException:
            log.seek(0)
            diagnostics = log.read()
            if diagnostics:
                print("Logs do proxy:", file=sys.stderr)
                print(diagnostics, file=sys.stderr)
            raise
        finally:
            env.pop("PGPASSWORD", None)
            password = None
            if proxy.poll() is None:
                proxy.terminate()
                try:
                    proxy.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    proxy.kill()
                    proxy.wait()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", nargs="?", default="validate", choices=("validate", "apply", "reset-data"))
    args = parser.parse_args()
    try:
        run_schema(args.mode)
    except (Exception, KeyboardInterrupt) as error:
        print(f"[FALHOU] {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
