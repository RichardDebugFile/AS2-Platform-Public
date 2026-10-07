#!/usr/bin/env python3
"""Publica el resultado del análisis de SonarQube Cloud en las historias de Jira referenciadas.

Lo invoca el workflow .github/workflows/sonar.yml después del análisis. Busca claves de Jira
(SCRUM-XX) e identificadores de historia (HU-XX) en la rama, el título del PR y los mensajes de
commit; obtiene de SonarQube Cloud el estado del Quality Gate y las métricas del análisis, y deja
en cada historia un comentario (que se actualiza en los análisis siguientes del mismo PR o rama)
y un enlace al panel de SonarQube Cloud.

Solo usa la biblioteca estándar. Si faltan las credenciales de Jira, termina sin error.
"""
import base64
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request

SONAR_HOST = os.environ.get("SONAR_HOST_URL", "https://sonarcloud.io").rstrip("/")
SONAR_TOKEN = os.environ.get("SONAR_TOKEN", "")
PROJECT = os.environ.get("SONAR_PROJECT_KEY", "")
JIRA_URL = os.environ.get("JIRA_BASE_URL", "").rstrip("/")
JIRA_USER = os.environ.get("JIRA_USER_EMAIL", "")
JIRA_TOKEN = os.environ.get("JIRA_API_TOKEN", "")
JIRA_PROJECT = os.environ.get("JIRA_PROJECT_KEY", "SCRUM")
SCAN_OUTCOME = os.environ.get("SCAN_OUTCOME", "")

BRANCH_METRICS = [
    ("coverage", "Cobertura", "%"),
    ("tests", "Pruebas", ""),
    ("bugs", "Bugs", ""),
    ("vulnerabilities", "Vulnerabilidades", ""),
    ("security_hotspots", "Security hotspots", ""),
    ("code_smells", "Code smells", ""),
    ("duplicated_lines_density", "Duplicación", "%"),
    ("ncloc", "Líneas de código", ""),
]
PR_METRICS = [
    ("new_coverage", "Cobertura (código nuevo)", "%"),
    ("new_bugs", "Bugs nuevos", ""),
    ("new_vulnerabilities", "Vulnerabilidades nuevas", ""),
    ("new_security_hotspots", "Security hotspots nuevos", ""),
    ("new_code_smells", "Code smells nuevos", ""),
    ("new_duplicated_lines_density", "Duplicación (código nuevo)", "%"),
]


def notice(msg):
    print(f"::notice title=Sonar -> Jira::{msg}")


def warn(msg):
    print(f"::warning title=Sonar -> Jira::{msg}")


def http(method, url, headers, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method, headers=headers)
    if data is not None:
        req.add_header("Content-Type", "application/json")
    with urllib.request.urlopen(req, timeout=30) as resp:
        raw = resp.read()
        return json.loads(raw) if raw else {}


def sonar_get(path, params):
    headers = {"Accept": "application/json"}
    if SONAR_TOKEN:
        headers["Authorization"] = "Bearer " + SONAR_TOKEN
    return http("GET", f"{SONAR_HOST}{path}?{urllib.parse.urlencode(params)}", headers)


def jira(method, path, body=None):
    auth = base64.b64encode(f"{JIRA_USER}:{JIRA_TOKEN}".encode()).decode()
    headers = {"Authorization": "Basic " + auth, "Accept": "application/json"}
    return http(method, f"{JIRA_URL}{path}", headers, body)


def github_context():
    """Devuelve (tipo, número de PR o nombre de rama, textos donde buscar claves, título)."""
    event = {}
    path = os.environ.get("GITHUB_EVENT_PATH")
    if path and os.path.exists(path):
        with open(path, encoding="utf-8") as fh:
            event = json.load(fh)
    texts = []
    pr = event.get("pull_request")
    if pr:
        texts += [pr.get("title", ""), pr.get("head", {}).get("ref", ""), pr.get("body") or ""]
        label = f"PR #{pr['number']} ({pr.get('head', {}).get('ref', '')})"
        return "pr", str(pr["number"]), texts, label, pr.get("html_url", "")
    branch = os.environ.get("GITHUB_REF_NAME", "")
    texts.append(branch)
    for commit in event.get("commits", []) or []:
        texts.append(commit.get("message", ""))
    head = event.get("head_commit") or {}
    texts.append(head.get("message", ""))
    return "branch", branch, texts, f"rama {branch}", ""


def find_issue_keys(texts):
    blob = "\n".join(t for t in texts if t)
    keys = set(re.findall(rf"\b{re.escape(JIRA_PROJECT)}-\d+\b", blob))
    nums = set()
    # "HU-02", "HU 2" y también listas en ramas como "feature/HU-02-03-04-identidad".
    for group in re.findall(r"\bHU[- ]?(\d{1,3}(?:-\d{2}(?![\d]))*)", blob, flags=re.IGNORECASE):
        nums.update(group.split("-"))
    for num in sorted(nums):
        hu = f"HU-{int(num):02d}"
        jql = f'project = {JIRA_PROJECT} AND summary ~ "\\"{hu}\\""'
        try:
            res = jira("GET", "/rest/api/3/search/jql?" + urllib.parse.urlencode(
                {"jql": jql, "fields": "summary", "maxResults": 10}))
        except urllib.error.HTTPError as exc:
            warn(f"No se pudo resolver {hu} en Jira ({exc.code}).")
            continue
        for issue in res.get("issues", []):
            summary = issue.get("fields", {}).get("summary", "")
            if re.match(rf"{hu}\b", summary):
                keys.add(issue["key"])
    return sorted(keys, key=lambda k: int(k.split("-")[1]))


def sonar_results(kind, ref):
    scope = {"pullRequest": ref} if kind == "pr" else {"branch": ref}
    gate = "NONE"
    try:
        gate = sonar_get("/api/qualitygates/project_status",
                         {"projectKey": PROJECT, **scope})["projectStatus"]["status"]
    except (urllib.error.HTTPError, urllib.error.URLError, KeyError) as exc:
        warn(f"No se pudo leer el Quality Gate: {exc}")
    wanted = PR_METRICS if kind == "pr" else BRANCH_METRICS
    values = {}
    for key, _, _ in wanted:  # de una en una: una métrica inexistente no invalida las demás
        try:
            comp = sonar_get("/api/measures/component",
                             {"component": PROJECT, "metricKeys": key, **scope})["component"]
            for m in comp.get("measures", []):
                values[m["metric"]] = m.get("value") or (m.get("period") or {}).get("value")
        except (urllib.error.HTTPError, urllib.error.URLError, KeyError):
            continue
    rows = [(label, (values[k] + unit) if values.get(k) is not None else "—")
            for k, label, unit in wanted]
    page = "new_code" if kind == "pr" else "overall"
    param = "pullRequest" if kind == "pr" else "branch"
    url = f"{SONAR_HOST}/summary/{page}?id={urllib.parse.quote(PROJECT)}&{param}={urllib.parse.quote(ref)}"
    return gate, rows, url


def comment_body(marker, label, gate, rows, sonar_url, pr_url):
    icon = {"OK": "(/)", "ERROR": "(x)", "WARN": "(!)"}.get(gate, "(?)")
    text = {"OK": "Aprobado", "ERROR": "Rechazado", "WARN": "Advertencia"}.get(gate, "Sin datos")
    server = os.environ.get("GITHUB_SERVER_URL", "https://github.com")
    repo = os.environ.get("GITHUB_REPOSITORY", "")
    run = f"{server}/{repo}/actions/runs/{os.environ.get('GITHUB_RUN_ID', '')}"
    sha = os.environ.get("GITHUB_SHA", "")[:7]
    lines = [
        f"h4. SonarQube Cloud: {label}",
        f"*Quality Gate:* {icon} {text}",
        "",
        "||Métrica||Valor||",
        *[f"|{name}|{value}|" for name, value in rows],
        "",
        f"[Ver análisis en SonarQube Cloud|{sonar_url}] · [Ejecución del workflow|{run}]"
        + (f" · [Pull request|{pr_url}]" if pr_url else ""),
        f"Commit {sha} · análisis {SCAN_OUTCOME or 'n/d'}",
        "",
        f"{{{{{marker}}}}}",
    ]
    return "\n".join(lines)


def upsert_comment(key, marker, body):
    comments = jira("GET", f"/rest/api/2/issue/{key}/comment?maxResults=100").get("comments", [])
    for c in comments:
        if marker in (c.get("body") or ""):
            jira("PUT", f"/rest/api/2/issue/{key}/comment/{c['id']}", {"body": body})
            return "actualizado"
    jira("POST", f"/rest/api/2/issue/{key}/comment", {"body": body})
    return "creado"


def upsert_link(key, global_id, title, url):
    jira("POST", f"/rest/api/2/issue/{key}/remotelink", {
        "globalId": global_id,
        "object": {"url": url, "title": title,
                   "icon": {"url16x16": f"{SONAR_HOST}/favicon.ico", "title": "SonarQube Cloud"}},
    })


def main():
    if not (JIRA_URL and JIRA_USER and JIRA_TOKEN):
        notice("Sin JIRA_BASE_URL / JIRA_USER_EMAIL / JIRA_API_TOKEN: se omite el reporte en Jira.")
        return 0
    if not PROJECT:
        warn("SONAR_PROJECT_KEY vacío: no se puede consultar SonarQube Cloud.")
        return 0
    kind, ref, texts, label, pr_url = github_context()
    try:
        keys = find_issue_keys(texts)
    except urllib.error.URLError as exc:
        warn(f"Jira no responde: {exc}")
        return 0
    if not keys:
        notice(f"No hay claves {JIRA_PROJECT}-XX ni HU-XX en la rama, el PR o los commits.")
        return 0
    gate, rows, sonar_url = sonar_results(kind, ref)
    marker = f"sonar-report:{PROJECT}:{kind}:{ref}"
    body = comment_body(marker, label, gate, rows, sonar_url, pr_url)
    for key in keys:
        try:
            action = upsert_comment(key, marker, body)
            upsert_link(key, marker, f"SonarQube Cloud: {label}", sonar_url)
            print(f"{key}: comentario {action} (Quality Gate {gate}).")
        except urllib.error.HTTPError as exc:
            warn(f"{key}: Jira respondió {exc.code} {exc.read()[:200]!r}")
    with open(os.environ.get("GITHUB_STEP_SUMMARY", os.devnull), "a", encoding="utf-8") as fh:
        fh.write(f"## Reporte en Jira\nQuality Gate **{gate}** publicado en: {', '.join(keys)}\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
