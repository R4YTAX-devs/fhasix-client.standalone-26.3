#!/usr/bin/env python3
"""Dependency-free structural checks for the FHASIX Fabric source tree.

This does not replace Gradle/javac. It checks project structure and resource
metadata before compilation so simple problems are reported alongside compiler
errors rather than requiring another build attempt.
"""
from pathlib import Path
import json
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "src/main/java"
RES = ROOT / "src/main/resources"
errors = []
notes = []
java_files = sorted(SRC.rglob("*.java"))
if not java_files:
    errors.append("No Java source files found under src/main/java")

# Lexical brace check: ignore comments, quoted strings/chars and Java text blocks.
def brace_balance(source):
    i = 0
    n = len(source)
    depth = 0
    min_depth = 0
    state = "code"
    while i < n:
        c = source[i]
        nxt = source[i + 1] if i + 1 < n else ""
        if state == "code":
            if source.startswith('"""', i):
                state = "textblock"; i += 3; continue
            if c == '/' and nxt == '/':
                state = "line_comment"; i += 2; continue
            if c == '/' and nxt == '*':
                state = "block_comment"; i += 2; continue
            if c == '"': state = "string"; i += 1; continue
            if c == "'": state = "char"; i += 1; continue
            if c == '{': depth += 1
            elif c == '}':
                depth -= 1
                min_depth = min(min_depth, depth)
        elif state == "line_comment":
            if c == '\n': state = "code"
        elif state == "block_comment":
            if c == '*' and nxt == '/': state = "code"; i += 2; continue
        elif state == "textblock":
            if source.startswith('"""', i): state = "code"; i += 3; continue
            if c == '\\': i += 2; continue
        elif state in ("string", "char"):
            if c == '\\': i += 2; continue
            if (state == "string" and c == '"') or (state == "char" and c == "'"):
                state = "code"
        i += 1
    return depth, min_depth, state

# Map every declared type (including nested types) to its fully-qualified name.
declared_types = set()
for path in java_files:
    text = path.read_text(encoding="utf-8", errors="replace")
    rel = path.relative_to(SRC)
    package_match = re.search(r'^\s*package\s+([\w.]+)\s*;', text, re.M)
    package = package_match.group(1) if package_match else ""
    expected_parent = package.replace('.', '/')
    if expected_parent and str(rel.parent).replace('\\', '/') != expected_parent:
        errors.append(f"Package/path mismatch: {rel} declares package {package}")
    bal, min_bal, state = brace_balance(text)
    if bal != 0 or min_bal < 0 or state in ("block_comment", "string", "char", "textblock"):
        errors.append(f"Possible unbalanced Java syntax in {rel}: brace_balance={bal}, minimum={min_bal}, ending_state={state}")
    # Type declaration scan is used only for internal-import validation; nested names are legal.
    for match in re.finditer(r'\b(?:class|interface|enum|record)\s+(\w+)', text):
        declared_types.add((package + '.' if package else '') + match.group(1))

# Verify project-internal imports resolve to a declared type or a nested type prefix.
for path in java_files:
    text = path.read_text(encoding="utf-8", errors="replace")
    for imp in re.findall(r'^\s*import\s+(?:static\s+)?(com\.fhasix\.[\w.*]+)\s*;', text, re.M):
        if imp.endswith('.*'):
            prefix = imp[:-2]
            if not any(name == prefix or name.startswith(prefix + '.') for name in declared_types):
                errors.append(f"Internal wildcard import has no matching package/type: {path.relative_to(ROOT)} -> {imp}")
        elif imp not in declared_types and not any(name.startswith(imp + '.') for name in declared_types):
            errors.append(f"Unresolved internal import: {path.relative_to(ROOT)} -> {imp}")

# Validate all JSON resources.
for path in sorted(RES.rglob('*.json')) if RES.exists() else []:
    try:
        json.loads(path.read_text(encoding='utf-8'))
    except Exception as exc:
        errors.append(f"Invalid JSON: {path.relative_to(ROOT)}: {exc}")

# Verify mixin config names point to source files.
for config_path in sorted(RES.rglob('*.mixins.json')) if RES.exists() else []:
    try:
        config = json.loads(config_path.read_text(encoding='utf-8'))
        package = config.get('package', '')
        for name in config.get('mixins', []) + config.get('client', []) + config.get('server', []):
            source_path = SRC / Path(*package.split('.')) / (name + '.java')
            if not source_path.exists():
                errors.append(f"Mixin source missing: {config_path.relative_to(ROOT)} references {name}, expected {source_path.relative_to(ROOT)}")
    except Exception as exc:
        errors.append(f"Could not inspect mixin config {config_path.relative_to(ROOT)}: {exc}")

# Confirm the expected version fields are present; Gradle remains the authority for resolution.
props = ROOT / 'gradle.properties'
if props.exists():
    values = {}
    for line in props.read_text(encoding='utf-8', errors='replace').splitlines():
        line = line.strip()
        if line and not line.startswith('#') and '=' in line:
            k, v = line.split('=', 1); values[k.strip()] = v.strip()
    for key in ('minecraft_version', 'yarn_mappings', 'loader_version', 'fabric_version'):
        if not values.get(key): errors.append(f"Missing required Gradle property: {key}")
    if values.get('minecraft_version') != '1.21.11':
        notes.append(f"Configured Minecraft version is {values.get('minecraft_version')!r}, expected 1.21.11")

report = [
    'FHASIX source preflight report',
    f'Java files scanned: {len(java_files)}',
    f'JSON resources scanned: {len(list(RES.rglob("*.json"))) if RES.exists() else 0}',
    'Checks: package/path consistency, Java brace/comment/string structure, project-internal imports, JSON syntax, mixin source references, version properties.',
    'Limit: this is not a Java compiler and cannot verify Minecraft/Fabric method signatures. Gradle/javac must still pass.',
    '',
]
report += [f'NOTE: {item}' for item in notes]
report += [f'ERROR: {item}' for item in errors]
if not errors and not notes:
    report.append('PASS: all structural preflight checks passed.')
report_text = '\n'.join(report) + '\n'
print(report_text, end='')
output = ROOT / 'build' / 'preflight-report.txt'
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(report_text, encoding='utf-8')
sys.exit(1 if errors else 0)
