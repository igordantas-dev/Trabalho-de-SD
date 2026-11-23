"""Offline checks: password handling and cleanup of the schema runner."""
import importlib.util
from pathlib import Path
import subprocess
import unittest
from unittest.mock import Mock, patch

spec = importlib.util.spec_from_file_location(
    "cloudsql_schema", Path(__file__).with_name("CloudSql-Schema.py")
)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class SchemaRunnerTest(unittest.TestCase):
    def setUp(self):
        self.proxy = Mock()
        self.proxy.poll.return_value = None
        self.received_env = None
        self.psql_args = None
        self.psql_error = None

    def fake_run(self, args, **kwargs):
        if args[0] == "gcloud":
            return subprocess.CompletedProcess(args, 0, stdout="test-only-password\n")
        self.received_env = dict(kwargs["env"])
        self.psql_args = args
        if self.psql_error:
            raise self.psql_error
        return subprocess.CompletedProcess(args, 0)

    def execute(self, mode):
        with patch.object(module.shutil, "which", side_effect=lambda command: command), \
             patch.object(module, "free_port", return_value=54321), \
             patch.object(module, "wait_proxy"), \
             patch.object(module.subprocess, "Popen", return_value=self.proxy), \
             patch.object(module.subprocess, "run", side_effect=self.fake_run):
            module.run_schema(mode)

    def test_validate_uses_password_env_and_disables_prompts(self):
        self.execute("validate")
        self.assertIn("-w", self.psql_args)
        self.assertNotIn("test-only-password", self.psql_args)
        self.assertEqual(self.received_env["PGPASSWORD"], "test-only-password")
        self.assertTrue(self.psql_args[-1].endswith("Validate.psql"))
        self.proxy.terminate.assert_called_once()

    def test_apply_selects_transactional_baseline(self):
        self.execute("apply")
        self.assertTrue(self.psql_args[-1].endswith("Apply.psql"))
        self.assertIn("ON_ERROR_STOP=1", self.psql_args)
        self.proxy.wait.assert_called_once_with(timeout=5)

    def test_sql_error_still_stops_proxy(self):
        self.psql_error = subprocess.CalledProcessError(3, ["psql"])
        with self.assertRaises(subprocess.CalledProcessError):
            self.execute("validate")
        self.proxy.terminate.assert_called_once()


if __name__ == "__main__":
    unittest.main()
