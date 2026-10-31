# Remote ADB: Kết nối ADB Không Cần Cùng Mạng & Không Cần Cắm Dây (Root-Powered)

Giải pháp kết nối ADB từ máy tính (PC) sang điện thoại Android từ xa qua Internet (4G / Wifi khác mạng) mà **không cần cắm cáp USB**, **không cần bật thủ công Developer Options** và **không hiện popup xác nhận USB Debugging** nhờ tận dụng quyền Root (Magisk, KernelSU, APatch, SuperSU).

---

## 🚀 Tính năng Nổi Bật

1. **Root-Powered ADB Activation**:
   - Tự động kích hoạt ADB qua TCP/IP (`setprop service.adb.tcp.port 5555`) mà không cần cắm dây trước.
   - Hỗ trợ toàn diện các giải pháp Root: **Magisk**, **KernelSU**, **APatch**, **SuperSU**.
2. **Bypass Popup "Allow USB Debugging"**:
   - Tự động ghi nhận Public Key của PC (`adbkey.pub`) vào thẳng `/data/misc/adb/adb_keys` của hệ điều hành.
   - Kết nối là chạy ngay, không cần thao tác bấm "Chấp nhận" trên màn hình điện thoại.
3. **Kết Nối Bất Kỳ Đâu Qua Internet**:
   - Chuyển tiếp kết nối bằng Reverse TCP Tunnel WebSocket siêu nhẹ qua Relay Server.
   - Hoạt động ổn định trên cả mạng di động 4G/5G, Wifi mạng khác, chống NAT / CGNAT.
4. **Foreground Service Chạy Ngầm Bền Bỉ**:
   - Duy trì kết nối liên tục, tự động reconnect khi chuyển mạng hoặc chập chờn.
5. **PC CLI & Script 1-Click**:
   - Hỗ trợ PowerShell (Windows), Bash (Linux/macOS), và Node.js CLI `adb-remote`.
   - Tích hợp 1-click mở **scrcpy** để điều khiển màn hình điện thoại từ xa qua mạng.

---

## 📂 Cấu Trúc Dự Án

- `android-app/`: Ứng dụng Android (Kotlin 2.0, Jetpack Compose, Material 3, `libsu`).
- `relay-server/`: Relay Server trung gian (Node.js/TypeScript, hỗ trợ Docker / docker-compose).
- `pc-tool/`: Công cụ CLI và script PowerShell/Bash kết nối từ máy tính.
- `docs/`: Tài liệu chi tiết triển khai máy chủ và hướng dẫn sử dụng.

---

## ⚡ Hướng Dẫn Sử Dụng Nhanh (Quick Start)

### Bước 1: Khởi động Relay Server (VPS hoặc Máy chủ công khai)
```bash
cd relay-server
docker compose up -d
```
*Server sẽ mở cổng điều khiển `8080` và dải cổng dynamic ADB `15000-15050`.*

### Bước 2: Cài đặt và Chạy Android App
1. Mở thư mục `android-app` trong **Android Studio** và build file APK cài đặt lên điện thoại.
2. Mở app và cấp quyền Root (**Grant Superuser Access**).
3. Vào tab **Settings** -> Nhập IP/Domain VPS của bạn.
4. Vào tab **Home** -> Bật switch **ADB Daemon (TCP:5555)** -> Bấm **Start Tunnel Relay**.

### Bước 3: Thêm PC Key để Bypass Popup (Chỉ cần làm 1 lần)
Trên máy tính:
```powershell
# Windows PowerShell
cd pc-tool
.\adb-remote.ps1 -ShowKey
```
Copy chuỗi Public Key hiển thị và dán vào tab **PC Keys** trong Android App.

### Bước 4: Kết nối từ xa trên PC
```powershell
# Kết nối ADB trực tiếp
adb connect <IP_SERVER>:15000

# Hoặc dùng script tự động mở scrcpy
.\adb-remote.ps1 -Server <IP_SERVER> -Port 15000 -Scrcpy
```

---

## 📖 Tài Liệu Tham Khảo Thêm
- [Hướng dẫn Sử dụng Chi tiết](docs/USAGE_GUIDE.md)
- [Hướng dẫn Triển khai Relay Server & Nginx SSL](docs/SERVER_DEPLOYMENT.md)
