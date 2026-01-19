# Monitoring and Performance Guide

## 1. Minimizing Thread Pool Usage
We have enabled **Virtual Threads** (Project Loom) in your application.
- **Config:** `spring.threads.virtual.enabled=true`
- **Impact:** Instead of a large pool of OS threads (e.g., 200), the application now creates lightweight virtual threads for every request. This drastically reduces memory footprint per connection and increases throughput for I/O-bound tasks (like database calls).

## 2. Managing & Monitoring Memory Leaks

### A. Immediate Diagnosis (Heap Dumps)
Since you have `spring-boot-starter-actuator` enabled, you can capture a heap dump when memory usage is high.

1.  **Download Heap Dump:**
    ```bash
    curl -O http://localhost:8080/actuator/heapdump
    ```
    *(Note: In production, ensure this endpoint is secured!)*

2.  **Analyze with Eclipse MAT (Memory Analyzer Tool):**
    - Open the `heapdump` file in Eclipse MAT.
    - Run the **"Leak Suspects Report"**.
    - It will identify objects retaining large amounts of memory (e.g., large Lists, static Maps, or unclosed streams).

### B. Continuous Monitoring (Prometheus & Grafana)
**Status: Configured ✅**

We have set up a full monitoring stack in `deployment/docker-compose.yml`.

**1. Accessing the Dashboards:**
*   **Prometheus:** [http://localhost:9090](http://localhost:9090) (Metrics collection)
*   **Grafana:** [http://localhost:3000](http://localhost:3000) (Visualization)
    *   **Login:** admin / admin

**2. Setting up Grafana:**
1.  Go to **Configuration (Gear Icon) -> Data Sources**.
2.  Click **Add data source** -> Select **Prometheus**.
3.  URL: `http://prometheus:9090` (Internal Docker DNS).
4.  Click **Save & Test**.
5.  Go to **Dashboards -> Import**.
6.  Enter ID **11378** (Spring Boot 2.1 System Monitor) or **4701** (JVM (Micrometer)).
7.  Click **Load** and select the Prometheus data source.

#### Implementation Details:
- **Dependency:** Added `io.micrometer:micrometer-registry-prometheus` to `build.gradle`.
- **Config:** Created `deployment/prometheus/prometheus.yml`.
- **Infrastructure:** Added Prometheus and Grafana containers to `docker-compose.yml`.

## 4. Accessing from VPS (Remote Server)
Since these are administrative tools, **do not expose ports 3000 and 9090 to the public internet** unless you have secured them with a reverse proxy (like Nginx) and SSL.

### Option A: SSH Tunneling (Recommended & Secure)
This allows you to access the remote Grafana instance as if it were running on your local machine.

1.  **Run this command on your local machine:**
    ```bash
    # Syntax: ssh -L local_port:localhost:remote_port user@vps_ip
    ssh -L 3000:localhost:3000 -L 9090:localhost:9090 user@your-vps-ip
    ```
2.  **Open your browser:**
    *   Grafana: [http://localhost:3000](http://localhost:3000)
    *   Prometheus: [http://localhost:9090](http://localhost:9090)

### Option B: Direct Access (Less Secure)
If you must access it directly via IP (e.g., `http://1.2.3.4:3000`), ensure you have a strong password for Grafana.

1.  **Allow ports in firewall (UFW):**
    ```bash
    sudo ufw allow 3000/tcp
    sudo ufw allow 9090/tcp
    ```
2.  **Access via Browser:**
    *   `http://<VPS_IP>:3000`

## 5. JVM Memory Configuration
Ensure your Docker container has explicit memory limits to avoid the container being killed by the OS (OOMKilled).

**Example `docker-compose.yml` update:**
```yaml
services:
  app:
    environment:
      - JAVA_TOOL_OPTIONS=-Xms512m -Xmx1024m
    deploy:
      resources:
        limits:
          memory: 1536M
```
