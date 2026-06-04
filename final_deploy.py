#!/usr/bin/env python3
import subprocess, os, json, time, urllib.request as ur

project = "/home/kali/projects/reddit-crawler"

print("=" * 70)
print("FINAL DEPLOYMENT CYCLE - ALL FIXES APPLIED")
print("=" * 70)

# Phase 1: Cleanup
print("\n[PHASE 1] Infrastructure Reset")
result = subprocess.run(["docker", "ps", "-q"], capture_output=True, text=True)
for cid in result.stdout.strip().split("\n"):
    if cid:
        subprocess.run(["docker", "stop", "-t", "15", cid], capture_output=True)
        
result = subprocess.run(
    ["docker", "rm", "-f"] + [c for c in result.stdout.strip().split("\n") if c],
    capture_output=True, text=True
)

for item_type, cmd in [("network", ["docker", "network", "ls", "-q"]), 
                       ("volume", ["docker", "volume", "ls", "-q"])]:
    result = subprocess.run(cmd, capture_output=True, text=True)
    for name in result.stdout.strip().split("\n"):
        if name and any(x in name.lower() for x in ["reddit", "firecrawl", "integrated"]):
            subprocess.run(["docker", item_type, "rm", name], capture_output=True, text=True)

subprocess.run(["docker", "builder", "prune", "-f"], capture_output=True)
time.sleep(1)
print("  Cleaned containers/networks/volumes/cache")

# Phase 2: Deploy
print("\n[PHASE 2] Building and Starting All Services")
result = subprocess.run(
    ["docker", "compose", "--profile", "all", "up", "-d", "--build"],
    capture_output=True, text=True, cwd=project, timeout=600
)
print(f"  Docker compose exit: {result.returncode}")

if result.returncode != 0:
    for line in (result.stdout + result.stderr).split("\n")[-40:]:
        if line.strip():
            low = line.lower()
            if any(x in low for x in ["error", "failed", "parse", "type error"]):
                print(f"  ERR> {line[-120:]}")

time.sleep(8)

# Phase 3: Verify
print("\n[PHASE 3] Post-Deploy Verification")
result = subprocess.run(
    ["docker", "ps", "--format", "{{.Names}}\t{{.Status}}"],
    capture_output=True, text=True
)
for line in result.stdout.strip().split("\n"):
    if line.strip():
        parts = line.split("\t")
        print(f"  RUNNING: {parts[0]:25} -> {parts[1]}")

pg = subprocess.run(
    ["docker", "inspect", "-f={{.State.Health.Status}}", "reddit-postgres"],
    capture_output=True, text=True
).stdout.strip()
print(f"\n  PostgreSQL: {pg}")

if pg == "healthy":
    time.sleep(5)
    api_logs = subprocess.run(
        ["docker", "logs", "--tail", str(80), "reddit-api"],
        capture_output=True, text=True
    ).stdout
    has_ok = any(x in api_logs.lower() for x in ["tomcat started", "started in ", "application running"])
    has_err = any(x in api_logs.lower() for x in ["connection attempt failed", "unknownhostexception"])
    print(f"  API startup: {'OK' if has_ok else 'UNKNOWN'}")
    status = "no-db-errors" if not has_err else "HAS-DB-ERRORS"
    print(f"  API DB connection: {status}")

for url, label in [
    ("http://localhost:8080/api/analysis/sentiment", "/api/analysis/sentiment"),
    ("http://localhost:3000/", "frontend :3000")
]:
    try:
        resp = ur.urlopen(url, timeout=5)
        data = resp.read()
        print(f"  GET {label} -> HTTP {resp.getcode()} ({len(data)} bytes)")
    except Exception as e:
        print(f"  GET {label} -> FAIL ({e})")

print("\n" + "=" * 70)
if result.returncode == 0 and pg == "healthy":
    print("[SUCCESS] Full deployment cycle complete!")
else:
    issues = []
    if "reddit-api" not in result.stdout.lower():
        issues.append("API missing")
    if pg != "healthy":
        issues.append(f"Postgres {pg}")
    print(f"[ISSUES DETECTED]: {'; '.join(issues) if issues else 'see logs above'}")
print("=" * 70)
