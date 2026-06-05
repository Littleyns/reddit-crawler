"""Production deployment orchestrator for Reddit Crawler Dev Loop."""
import subprocess
import sys
import os

REPO_ROOT = os.path.dirname(os.path.abspath(__file__))
BACKEND_DIR = os.path.join(REPO_ROOT, 'backend-java')
FRONTEND_DIR = os.path.join(REPO_ROOT, 'frontend')


def run(cmd):
    """Run a shell command and exit on failure."""
    print(f'$ {cmd}')
    res = subprocess.run(
        cmd, shell=True, capture_output=True, text=True
    )
    if res.returncode != 0:
        print(res.stderr[:300])
        sys.exit(1)
    return res.stdout


if __name__ == '__main__':
    print('=== Reddit Crawler Deployment ===')

    # 1. Backend compilation check
    run(f'mvn clean compile -DskipTests', cwd=BACKEND_DIR)

    # 2. Frontend production build
    env = os.environ.copy()
    env['CI'] = 'true'
    run('npm ci && npm run build', cwd=FRONTEND_DIR, env=env)

    # 3. Docker deployment
    run('docker compose down --remove-orphans')
    run('docker compose up -d')

    print('\nDeployment complete!')
