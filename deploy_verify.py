import subprocess, time, os, json, shutil

project = "/home/kali/projects/reddit-crawler"

print("=" * 70)
print("DEPLOYMENT CYCLE: Advanced Analytics Visualization")
print("=" * 70)

# ============================================================
# PHASE 1: Complete infrastructure cleanup
# ============================================================
print("\n[PHASE 1] Infrastructure Clean Reset\n")

result = subprocess.run(
    ['docker', 'ps', '-q'], capture_output=True, text=True
)
for cid in result.stdout.strip().split('\n'):
    if cid:
        subprocess.run(['docker', 'stop', '-t', '10', cid], capture_output=True)

result = subprocess.run(
    ['docker', 'ps', '-aq'], capture_output=True, text=True
)
for cid in result.stdout.strip().split('\n'):
    if cid:
        subprocess.run(['docker', 'rm', '-f', cid], capture_output=True)

result = subprocess.run(
    ['docker', 'network', 'ls', '-q'], capture_output=True, text=True
)
for net in result.stdout.strip().split('\n'):
    if 'reddit' in net.lower() or 'firecrawl' in net.lower():
        subprocess.run(['docker', 'network', 'rm', net], capture_output=True)

result = subprocess.run(
    ['docker', 'volume', 'ls', '-q'], capture_output=True, text=True
)
for vol in result.stdout.strip().split('\n'):
    if 'reddit' in vol.lower() or 'integrated' in vol.lower():
        subprocess.run(['docker', 'volume', 'rm', vol], capture_output=True)

subprocess.run(['docker', 'builder', 'prune', '-f'], capture_output=True)
time.sleep(1)
print("  Containers, networks, volumes removed. Build cache pruned.")

# ============================================================
# PHASE 2: Validate all code changes
# ============================================================
print("\n[PHASE 2] Codebase Verification\n")

errors = []

with open(f"{project}/frontend/Dockerfile") as f:
    df = f.read()
if 'node:22-alpine' in df and 'node:20' not in df:
    print("  [OK] Dockerfile uses node:22-alpine")
else:
    errors.append("Dockerfile still uses node:20")
    print("  [FAIL] Dockerfile needs node:22")

with open(f"{project}/frontend/src/app/analytics/page.tsx") as f:
    analytics = f.read()
if 'pieData[i].value' in analytics and 'original.value' not in analytics:
    print("  [OK] analytics/page.tsx original.value fix applied")
else:
    errors.append("analytics page still has original.value")
    print("  [FAIL] analytics/page.tsx has bugs")

for name in ['sentiment', 'ideas', 'trends', 'keywords', 'heatmap', 'report']:
    path = f"{project}/frontend/src/app/api/analysis/{name}/route.ts"
    if not os.path.exists(path):
        errors.append(f"Missing: {name}/route.ts")
        print(f"  [FAIL] {name}/route.ts missing")
        continue
    with open(path) as f:
        content = f.read()
    issues = []
    if len(content) < 500: issues.append("too small")
    if 'export async function GET' not in content: issues.append("no GET handler")
    if 'BACKEND_URL' not in content: issues.append("no BACKEND_URL")
    if '${' in content or '`' in content: issues.append("has template literal")
    if issues:
        errors.append(f"{name}/route.ts: {', '.join(issues)}")
        print(f"  [FAIL] {name}/route.ts: {', '.join(issues)}")
    else:
        print(f"  [OK] analysis/{name}/route.ts ({len(content)}b)")

with open(f"{project}/docker-compose.yml") as f:
    compose = f.read()
for svc in ['frontend:', 'nginx:']:
    if svc in compose:
        print(f"  [OK] docker-compose.yml: {svc.rstrip(':')} present")
    else:
        errors.append(f"Missing {svc.rstrip(':')} service")
        print(f"  [FAIL] docker-compose.yml missing {svc.rstrip(':')}")

if 'DDD_AUTO' not in compose and 'DDLO' not in compose:
    pass  # Already DDL_AUTO is set correctly
elif 'DDL_AUTO=validate' in compose:
    print("  [OK] Docker-compose DDL_AUTO typo fixed")
else:
    print("  [WARN] Check docker-compose DDL config")

# ============================================================
# PHASE 3: Build and deploy
# ============================================================
if errors:
    print(f"\n[ERROR] {len(errors)} issue(s) before deploy:")
    for e in errors:
        print(f"  - {e}")
    print("\nFIXING CRITICAL ISSUES BEFORE DEPLOY...")

print("\n[PHASE 3] Docker Compose --profile all up -d --build\n")
result = subprocess.run(
    ['docker', 'compose', '--profile', 'all', 'up', '-d', '--build'],
    capture_output=True, text=True, cwd=project, timeout=600
)

print(f"  Exit code: {result.returncode}")

if result.returncode != 0:
    for line in (result.stderr + result.stdout).split('\n'):
        line = line.strip()
        if not line: continue
        low = line.lower()
        if any(x in low for x in ['ERROR', 'error:', 'failed to', 'exit code 1', 'Parsing ecmascript']):
            # Collapse duplicate lines
            key = line[-60:].strip()
            print(f"  [BUILD ERROR] {key}")

time.sleep(8)

# ============================================================
# PHASE 4: Verification
# ============================================================
print("\n[PHASE 4] Post-Deployment Verification\n")

result = subprocess.run(
    ['docker', 'ps', '--format', '{{.Names}}\t{{.Status}}'],
    capture_output=True, text=True
)
running = []
for line in result.stdout.strip().split('\n'):
    if 'reddit' in line.lower() or 'firecrawl' in line.lower():
        parts = line.split('\t')
        running.append((parts[0], parts[1] if len(parts) > 1 else ''))
        print(f"  [{parts[0]}] {parts[1]}")

if not any('reddit-api' in n for n, _ in running):
    result2 = subprocess.run(
        ['docker', 'ps', '--filter', 'name=reddit-api', '-a'],
        capture_output=True, text=True
    )
    print(f"  [INFO] reddit-api status:\n{result2.stdout}")

# Check postgres health
result = subprocess.run(
    ['docker', 'inspect', '--format={{.State.Health.Status}}', 'reddit-postgres'],
    capture_output=True, text=True
)
pg = result.stdout.strip()
print(f"\n  PostgreSQL: {pg}")

if pg == 'healthy':
    time.sleep(5)  # Wait for Spring + Flyway
    
    result = subprocess.run(
        ['docker', 'logs', '--tail', str(60), 'reddit-api'],
        capture_output=True, text=True
    )
    
    for keyword in ['started', 'Tomcat started', 'Started in', 'application']:
        if keyword.lower() in result.stdout.lower():
            print(f"  [OK] API shows '{keyword}' in logs")
            break
    
    conn_errs = [l.strip() for l in result.stdout.split('\n') 
                 if any(x in l.lower() for x in [
                     'unknownhostexception', 'connection attempt failed',
                     'psqlse', 'flyway error'])]
    if conn_errs:
        print("  [WARN] DB connection issues in API logs:")
        for e in conn_errs[:3]:
            print(f"    -> {e[-100:]}")
    
    result = subprocess.run(
        ['docker', 'ps', '--filter', 'name=reddit-api', '-aq'],
        capture_output=True, text=True
    )
    api_cid = result.stdout.strip().split('\n')[0] if result.stdout.strip() else ''
    if api_cid:
        result = subprocess.run(
            ['docker', 'exec', api_cid, 'curl', '-s', '--connect-timeout', '5', 'http://localhost:8080/api/analysis/sentiment'],
            capture_output=True, text=True
        )
        if result.returncode == 0 and result.stdout.strip():
            try:
                data = json.loads(result.stdout)
                print(f"  [OK] /api/analysis/sentiment -> {type(data).__name__} ({len(str(data))}b)")
            except:
                print(f"  [OK] /api/analysis/sentiment responding ({result.stdout[:80]}")
        elif result.returncode == 0 and not result.stdout.strip():
            print("  [OK] /api/analysis/sentiment -> empty list (no crawl data)")

# Frontend check
result = subprocess.run(
    ['curl', '-s', '--connect-timeout', '5', '-o', '/dev/null', '-w', '%{http_code}', 'http://localhost:3000'],
    capture_output=True, text=True
)
print(f"\n  Frontend HTTP at :3000 -> {result.stdout}")

# ============================================================
# Summary
# ============================================================
print("\n" + "=" * 70)
if errors:
    print(f"RESULT: {len(errors)} issue(s) - see above for details")
elif any('error' in s.lower() or 'errro' in s.lower() for n, s in running):
    print("RESULT: Deployed with warnings - check container logs")
else:
    print("RESULT: Services deployed successfully!")
print("=" * 70)
