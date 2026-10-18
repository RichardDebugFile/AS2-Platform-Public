"""Pruebas del reporte SonarQube Cloud -> Jira (solo biblioteca estándar)."""
import os
import sys
import unittest
from unittest import mock

sys.path.insert(0, os.path.dirname(__file__))
import sonar_jira_report as rep  # noqa: E402


class MeasureValueTest(unittest.TestCase):
    def test_valor_de_rama(self):
        self.assertEqual(rep.measure_value({"metric": "coverage", "value": "91.1"}), "91.1")

    def test_codigo_nuevo_en_period(self):
        m = {"metric": "new_coverage", "period": {"index": 1, "value": "85.0"}}
        self.assertEqual(rep.measure_value(m), "85.0")

    def test_codigo_nuevo_en_periods(self):
        m = {"metric": "new_bugs", "periods": [{"index": 1, "value": "0"}]}
        self.assertEqual(rep.measure_value(m), "0")

    def test_sin_valor(self):
        self.assertIsNone(rep.measure_value({"metric": "new_coverage"}))


class FormatValueTest(unittest.TestCase):
    def test_porcentaje_con_coma(self):
        self.assertEqual(rep.format_value("91.1", "%"), "91,1 %")
        self.assertEqual(rep.format_value("0.0", "%"), "0,0 %")

    def test_entero(self):
        self.assertEqual(rep.format_value("3722", ""), "3722")
        self.assertEqual(rep.format_value("0", ""), "0")


class SonarResultsTest(unittest.TestCase):
    def fake_sonar(self, path, params):
        if path == "/api/qualitygates/project_status":
            return {"projectStatus": {"status": "OK", "conditions": [
                {"metricKey": "new_duplicated_lines_density", "actualValue": "1.5"}]}}
        key = params["metricKeys"]
        measures = {
            "new_coverage": [{"metric": "new_coverage", "period": {"value": "87.5"}}],
            "new_violations": [{"metric": "new_violations", "periods": [{"value": "2"}]}],
            "new_security_hotspots": [{"metric": "new_security_hotspots", "period": {"value": "0"}}],
        }.get(key, [])
        return {"component": {"measures": measures}}

    def test_pr_lee_period_periods_y_condiciones_del_gate(self):
        with mock.patch.object(rep, "PROJECT", "proj"), \
                mock.patch.object(rep, "sonar_get", side_effect=self.fake_sonar), \
                mock.patch("builtins.print"):
            gate, rows, url = rep.sonar_results("pr", "23")
        valores = dict(rows)
        self.assertEqual(gate, "OK")
        self.assertEqual(valores["Cobertura (código nuevo)"], "87,5 %")
        self.assertEqual(valores["Incidencias nuevas"], "2")
        self.assertEqual(valores["Security hotspots nuevos"], "0")
        self.assertEqual(valores["Duplicación (código nuevo)"], "1,5 %")
        self.assertEqual(valores["Bugs nuevos"], "n/d")
        self.assertIn("pullRequest=23", url)

    def test_metrica_que_falla_no_invalida_las_demas(self):
        def sonar(path, params):
            if path == "/api/qualitygates/project_status":
                return {"projectStatus": {"status": "ERROR"}}
            if params["metricKeys"] == "tests":
                raise rep.urllib.error.HTTPError("u", 404, "no", {}, None)
            return {"component": {"measures": [{"metric": params["metricKeys"], "value": "5"}]}}

        with mock.patch.object(rep, "PROJECT", "proj"), \
                mock.patch.object(rep, "sonar_get", side_effect=sonar), \
                mock.patch("builtins.print"):
            gate, rows, _ = rep.sonar_results("branch", "main")
        valores = dict(rows)
        self.assertEqual(gate, "ERROR")
        self.assertEqual(valores["Pruebas (servicios Java)"], "n/d")
        self.assertEqual(valores["Cobertura"], "5,0 %")


if __name__ == "__main__":
    unittest.main()
