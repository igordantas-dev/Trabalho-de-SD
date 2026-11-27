"""Verifica os limites enviados ao gcloud sem alterar recursos na nuvem."""
import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('scaling', Path(__file__).with_name('Scaling-Gcp.py'))
scaling = importlib.util.module_from_spec(spec)
spec.loader.exec_module(scaling)


class ScalingTest(unittest.TestCase):
    def check_mode(self, mode, minimum):
        with patch('sys.argv', ['Scaling-Gcp.py', mode]), \
             patch.object(scaling.subprocess, 'run') as run, \
             patch.object(scaling, 'status'), patch('builtins.print'):
            scaling.main()
        self.assertEqual(run.call_count, 5)
        for call in run.call_args_list:
            args = call.args[0]
            self.assertIn('--min=' + minimum, args)
            self.assertIn('--max=2', args)
            self.assertIn('--min-instances=0', args)
            self.assertIn('--max-instances=2', args)
            self.assertNotIn('--max-instances=1', args)

    def test_economia(self):
        self.check_mode('economia', '0')

    def test_apresentacao(self):
        self.check_mode('apresentacao', '2')

    def test_status_does_not_update_services(self):
        with patch('sys.argv', ['Scaling-Gcp.py']), \
             patch.object(scaling.subprocess, 'run') as run, \
             patch.object(scaling, 'status') as status:
            scaling.main()
        run.assert_not_called()
        status.assert_called_once()


if __name__ == '__main__':
    unittest.main()
