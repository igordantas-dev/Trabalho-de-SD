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


class CloudSqlTargetTest(unittest.TestCase):
    def run_mode(self, mode):
        proxy = Mock()
        proxy.poll.return_value = None
        with patch.object(module.shutil, 'which', return_value='/usr/bin/tool'), \
             patch.object(module, 'free_port', return_value=19470), \
             patch.object(module, 'wait_proxy'), \
             patch.object(module.subprocess, 'Popen', return_value=proxy) as start, \
             patch.object(module.subprocess, 'run', side_effect=[
                 subprocess.CompletedProcess([], 0, stdout='senha-apenas-de-teste\n'),
                 subprocess.CompletedProcess([], 0),
             ]) as run, patch('builtins.print'):
            module.run_schema(mode)
        proxy.terminate.assert_called_once()
        proxy.wait.assert_called_once()
        self.assertEqual(start.call_args.args[0][-1], 'sd2026:us-central1:gymflow-db')
        self.assertIn('--project=sd2026', run.call_args_list[0].args[0])
        args = run.call_args_list[1].args[0]
        self.assertEqual(args[args.index('-U') + 1], 'gymflow_app')
        self.assertEqual(args[args.index('-d') + 1], 'academia')
        self.assertEqual(args[args.index('-h') + 1], '127.0.0.1')
        self.assertEqual(args[args.index('-p') + 1], '19470')
        return Path(args[args.index('-f') + 1])

    def test_reset_uses_cloud_proxy_and_only_maintenance_script(self):
        self.assertEqual(self.run_mode('reset-data').name, 'Reset-cloud-data.psql')

    def test_apply_does_not_trigger_reset(self):
        self.assertEqual(self.run_mode('apply').name, 'Apply.psql')

    def test_invalid_mode_stops_before_subprocess(self):
        with patch.object(module.subprocess, 'run') as run:
            with self.assertRaises(ValueError):
                module.run_schema('reset-local')
        run.assert_not_called()



if __name__ == "__main__":
    unittest.main()
