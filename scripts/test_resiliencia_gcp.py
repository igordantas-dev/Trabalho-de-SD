"""Offline checks for the Cloud Run demo orchestration and rollback."""
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock, patch

spec = importlib.util.spec_from_file_location(
    "resiliencia_gcp", Path(__file__).with_name("Test-Resiliencia-Gcp.py")
)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class ResilienceDemoTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.snapshot = Path(directory.name) / "restore.json"
        self.demo = module.Demo(self.snapshot)
        config = {
            "status": {
                "conditions": [{"type": "Ready", "status": "True"}],
                "traffic": [{"percent": 100}],
            },
            "spec": {"template": {"spec": {"containers": [{"env": [
                {"name": "FINANCEIRO_HOST", "value": "financeiro.example.run.app"},
                {"name": "FINANCEIRO_PORT", "value": "443"},
            ]}]}}},
        }
        self.demo.service = Mock(side_effect=lambda name: (
            {"status": {"url": "https://frontend.example.run.app"}} if name == "gymflow-frontend"
            else config if not self.snapshot.exists()
            else {"status": {"latestReadyRevisionName": "fault-revision"}}
        ))
        self.demo.endpoint = Mock()
        self.demo.retry_logs = Mock()
        self.responses = [
            (200, {"token": "test-token"}),
            (200, [{"id": "student"}]),
            (200, []),
            (503, None),
            (200, [{"id": "student"}]),
            (200, []),
        ]
        self.demo.request = Mock(side_effect=self.responses)
        output = patch("sys.stdout", new=io.StringIO())
        output.start()
        self.addCleanup(output.stop)

    def test_success_restores_endpoint_and_confirms_recovery(self):
        self.demo.run()
        self.assertEqual(self.demo.endpoint.call_args_list[0].args, ("127.0.0.1", "1"))
        self.assertEqual(
            self.demo.endpoint.call_args_list[1].args, ("financeiro.example.run.app", "443")
        )
        self.demo.retry_logs.assert_called_once_with("fault-revision")
        self.assertFalse(self.snapshot.exists())
        self.assertEqual(self.demo.request.call_count, 6)

    def test_unexpected_http_result_still_restores_endpoint(self):
        self.responses[3] = (200, [])
        self.demo.request.side_effect = self.responses
        with self.assertRaisesRegex(RuntimeError, "esperado HTTP 503"):
            self.demo.run()
        self.assertEqual(
            self.demo.endpoint.call_args_list[-1].args, ("financeiro.example.run.app", "443")
        )
        self.assertFalse(self.snapshot.exists())

    def test_failed_restore_preserves_emergency_snapshot(self):
        self.demo.endpoint.side_effect = [None, RuntimeError("restore failed")]
        with self.assertRaisesRegex(RuntimeError, "restore failed"):
            self.demo.run()
        self.assertEqual(json.loads(self.snapshot.read_text())["port"], "443")
        self.demo.endpoint.side_effect = None
        self.demo.restore()
        self.assertFalse(self.snapshot.exists())

    def test_baseline_failure_does_not_modify_gateway(self):
        self.demo.request.side_effect = [(200, {"token": "x"}), (503, None)]
        with self.assertRaisesRegex(RuntimeError, "esperado HTTP 200"):
            self.demo.run()
        self.demo.endpoint.assert_not_called()
        self.assertFalse(self.snapshot.exists())

    def test_retry_evidence_requires_same_call(self):
        def entry(call, attempt, service="FinanceiroService"):
            return {"textPayload": (
                f"grpc_call={call} method=academia.proto.{service}/"
                f"ListarMensalidadesPorAluno attempt={attempt} transparent_retry=false"
            )}
        self.assertIsNone(module.evidence_group([
            entry(1, 1), entry(2, 2), entry(3, 3)
        ]))
        self.assertIsNone(module.evidence_group([
            entry(1, 1), entry(1, 2), entry(1, 3, "AlunoService")
        ]))
        evidence = module.evidence_group([
            entry(4, 1), entry(4, 2), entry(4, 3)
        ])
        self.assertEqual(len(evidence), 3)


if __name__ == "__main__":
    unittest.main()
