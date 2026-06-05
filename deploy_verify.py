"""Automated deployment verification for Reddit Crawler Dev Loop."""
import requests
import sys
from datetime import datetime


def check_backend(port=8080):
    """Verify Spring Boot backend is healthy."""
    url = f"http://localhost:{port}/actuator/health"
    
    try:
        resp = requests.get(url, timeout=5)
        if resp.status_code == 200:
            status = resp.json().get("status", "UNKNOWN")
            return {"healthy": status == "UP"}
    except requests.ConnectionError:
        pass
    return {"healthy": False}


def check_frontend(port=3000):
    """Verify Next.js frontend is reachable."""
    url = f"http://localhost:{port}/"
    
    try:
        resp = requests.get(url, timeout=5)
        return {"reachable": resp.status_code == 200}
    except requests.ConnectionError:
        pass
    return {"reachable": False}


def run_health_checks():
    """Run all health checks and return summary."""
    backend = check_backend()
    frontend = check_frontend()
    
    print(f"\n=== Deployment Health Check [{datetime.now().strftime('%Y-%m-%d %H:%M')}] ===")
    
    back_status = "UP" if backend.get("healthy") else "DOWN"
    front_status = "OK" if frontend.get("reachable") else "UNREACHABLE"
    
    print(f"Backend health: {back_status}")
    print(f"Frontend reachable: {front_status}")
    
    overall = backend.get("healthy") and frontend.get("reachable")
    status_str = "HEALTHY" if overall else "UNHEALTHY"
    print(f"Status: {status_str}")
    
    return {"overall": overall, "backend": backend, "frontend": frontend}


if __name__ == "__main__":
    result = run_health_checks()
    sys.exit(0 if result["overall"] else 1)
