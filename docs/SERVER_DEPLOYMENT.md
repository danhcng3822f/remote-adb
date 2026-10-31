# Hướng Dẫn Triển Khai Relay Server (Deployment Guide)

Relay Server là thành phần trung gian chuyển tiếp luồng TCP giữa PC và điện thoại Android, có thể chạy trên bất kỳ VPS Linux nào (Ubuntu, Debian, CentOS, AlmaLinux, v.v.).

---

## 1. Triển Khai Bằng Docker Compose (Khuyên Dùng)

### Bước 1: Cài đặt Docker & Docker Compose trên VPS
```bash
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh
```

### Bước 2: Clone / Copy thư mục `relay-server` lên VPS
```bash
git clone https://github.com/yourusername/Remote-ADB.git
cd Remote-ADB/relay-server
```

### Bước 3: Khởi chạy Server
```bash
docker compose up -d --build
```

### Bước 4: Mở Cổng Tường Lửa (Firewall / Security Group)
Đảm bảo VPS của bạn mở các cổng sau:
- `8080/TCP`: Cổng WebSocket Control Plane
- `15000-15050/TCP`: Dải cổng kết nối ADB động

Ví dụ với `ufw` trên Ubuntu:
```bash
sudo ufw allow 8080/tcp
sudo ufw allow 15000:15050/tcp
sudo ufw reload
```

---

## 2. Triển Khai Trực Tiếp Bằng Node.js (Systemd Service)

Nếu bạn không muốn dùng Docker:

```bash
# Cài đặt Node.js 20+
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt-get install -y nodejs

# Build và chạy server
cd relay-server
npm install
npm run build
npm start
```

### Tạo File Dịch Vụ Systemd (`/etc/systemd/system/remote-adb-relay.service`):
```ini
[Unit]
Description=Remote ADB Relay Server
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=/opt/remote-adb-relay
ExecStart=/usr/bin/node dist/server.js
Restart=always
RestartSec=5
Environment=PORT=8080
Environment=TCP_RANGE_START=15000
Environment=TCP_RANGE_END=16000

[Install]
WantedBy=multi-user.target
```

Kích hoạt dịch vụ:
```bash
sudo systemctl daemon-reload
sudo systemctl enable --now remote-adb-relay
```

---

## 3. Cấu Hình Nginx Reverse Proxy Với SSL (Tùy Chọn)

Nếu muốn sử dụng kết nối bảo mật `wss://` qua tên miền riêng:

```nginx
server {
    server_name relay.yourdomain.com;

    location /ws/device {
        proxy_pass http://127.0.0.1:8080/ws/device;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "Upgrade";
        proxy_set_header Host $host;
        proxy_read_timeout 86400s;
        proxy_send_timeout 86400s;
    }

    location /health {
        proxy_pass http://127.0.0.1:8080/health;
    }

    listen 443 ssl; # Managed by Certbot / Let's Encrypt
    ssl_certificate /etc/letsencrypt/live/relay.yourdomain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/relay.yourdomain.com/privkey.pem;
}
```
*Lưu ý: Các cổng ADB TCP (`15000-16000`) vẫn là luồng TCP trực tiếp (Stream) nên không qua HTTP proxy Nginx.*
