# Spec Thiết Kế Hệ Thống: Remote ADB Không Cần Cùng Mạng (Root-Powered)

## 1. Mục tiêu và Phạm vi (Goal & Scope)
Xây dựng giải pháp hoàn chỉnh cho phép kết nối máy tính (PC) tới thiết bị Android qua ADB mà:
- **Không cần dây cáp (USB)**.
- **Không cần chung mạng LAN / Wifi** (hoạt động qua mạng di động 4G/5G, Wifi khác mạng, Internet).
- **Không cần bật Developer Options thủ công** trước đó (nhờ quyền Root: Magisk, KernelSU, APatch, SuperSU).
- **Không hiện popup nhắc cấp quyền gỡ lỗi USB** (bằng cơ chế tự động ghi khóa public key vào hệ điều hành qua root).

Hệ thống bao gồm 3 thành phần chính:
1. **Android App**: Ứng dụng Android viết bằng Kotlin + Jetpack Compose, tích hợp `libsu` để quản lý Root và Reverse Tunnel Client.
2. **Relay Server**: Server chuyển tiếp kết nối viết bằng Node.js / TypeScript (hỗ trợ Docker / VPS), quản lý phiên kết nối và bridge dữ liệu TCP giữa PC và điện thoại.
3. **PC CLI Tool**: Tiện ích dòng lệnh / script cho Windows & Linux/macOS giúp kết nối nhanh hoặc chỉ cần dùng lệnh `adb connect` tiêu chuẩn.

---

## 2. Kiến trúc Kỹ thuật (Architecture)

```
[Android Phone (Rooted)]                     [Relay Server (Cloud/VPS)]             [PC Client]
   |                                                    |                                |
   |-- (1) Root: Enable adbd TCP:5555                   |                                |
   |-- (2) Root: Inject PC adbkey -> adb_keys           |                                |
   |                                                    |                                |
   |-- (3) Establish Control/Tunnel (WSS/TCP) --------> |                                |
   |       (Registers Device ID & Token)                |                                |
   |                                                    |<-- (4) adb connect Server:Port |
   |                                                    |    or PC CLI connect <DeviceID>|
   |                                                    |                                |
   |<================ (5) Data Bridge (ADB Streams) ===>|<===============================|
```

---

## 3. Chi tiết từng thành phần

### 3.1. Android Application (`android-app`)
- **Ngôn ngữ & Framework**: Kotlin, Jetpack Compose, Material 3, Coroutines, Flow.
- **Quản lý Root**: Sử dụng thư viện `com.github.topjohnwu.libsu:core` - chuẩn tương thích cao nhất cho Magisk, KernelSU, APatch.
  - Kiểm tra trạng thái root (`Shell.isAppGrantedRoot()`).
  - Lệnh kích hoạt ADB TCP:
    ```bash
    setprop service.adb.tcp.port 5555
    stop adbd
    start adbd
    ```
  - Lệnh tắt ADB TCP:
    ```bash
    setprop service.adb.tcp.port -1
    stop adbd
    start adbd
    ```
  - Inject PC Key vào `/data/misc/adb/adb_keys`:
    ```bash
    mkdir -p /data/misc/adb
    echo "<pc_adbkey_pub>" >> /data/misc/adb/adb_keys
    chmod 0640 /data/misc/adb/adb_keys
    chown system:shell /data/misc/adb/adb_keys
    ```
- **Tunnel Client (Engine)**:
  - WebSocket / TCP Client kết nối tới Relay Server.
  - Tự động reconnect khi mạng thay đổi hoặc mất kết nối.
  - Chạy trong **Foreground Service** với Notification hiển thị trạng thái kết nối.
  - Nhận luồng dữ liệu từ Relay Server và forward sang `127.0.0.1:5555` (ADB Daemon cục bộ).
- **Giao diện (UI)**:
  - Card hiển thị trạng thái Root (Loại root: Magisk / KernelSU / ...).
  - Switch bật/tắt ADB TCP/IP và chọn cổng cục bộ (mặc định 5555).
  - Quản lý Server Relay (nhập IP/Domain của VPS hoặc chọn server mặc định).
  - Sinh Device ID & Auth PIN ngẫu nhiên hoặc tùy chỉnh.
  - Card thông tin kết nối (Remote Host, Assigned Port, Command 1-Click Copy).
  - Quản lý ADB Keys (danh sách key đã tin cậy, chức năng thêm key trực tiếp hoặc quét QR từ PC).

### 3.2. Relay Server (`relay-server`)
- **Ngôn ngữ**: TypeScript / Node.js (nhẹ, hỗ trợ stream multiplexing cực tốt).
- **Tính năng**:
  - **Control Plane**: Lắng nghe kết nối từ Android App qua WebSocket/TCP, xác thực `Device ID` và `Auth Token`.
  - **Data Plane (Dynamic TCP Listener)**: Khi một thiết bị kết nối thành công, Server tự động cấp phát 1 cổng TCP ngẫu nhiên hoặc theo yêu cầu (ví dụ: `15555`).
  - **Multiplexing / Streaming**: Khi PC kết nối vào cổng TCP tương ứng, Server chuyển tiếp toàn bộ luồng byte qua tunnel tới Android App để đưa vào `adbd:5555`.
  - Hỗ trợ triển khai nhanh qua `Docker` và `docker-compose`.

### 3.3. PC Client Tool (`pc-tool`)
- Script PowerShell (`.ps1`) và Bash script (`.sh`) + Node.js CLI:
  - Tự động lấy file `~/.android/adbkey.pub` trên máy tính.
  - Hỗ trợ lệnh kết nối nhanh:
    `./adb-remote.ps1 -Server <IP> -Device <DeviceID> -Pin <PIN>`
  - Sau khi kết nối, tự động thực thi `adb connect <Server>:<Port>` và kiểm tra `adb devices`.

---

## 4. Bảo mật (Security Model)
1. **Authentication**: Mỗi thiết bị có một mã PIN / Token bí mật. Relay Server chỉ mở cổng và forward dữ liệu khi Device ID và PIN hợp lệ.
2. **ADB Authentication**: Tận dụng cơ chế mã hóa RSA khóa bất đối xứng có sẵn của ADB. Ngay cả khi cổng kết nối bị lộ trên internet, chỉ PC có khóa `adbkey` đã được cho phép (inject qua root) mới có thể thực thi lệnh shell.

---

## 5. Cấu trúc thư mục dự án (Project Structure)
```
/
├── android-app/             # Dự án Android Studio (Kotlin + Jetpack Compose)
│   ├── app/
│   │   ├── src/main/java/com/remoteadb/app/
│   │   │   ├── data/        # Data stores, Prefs
│   │   │   ├── root/        # RootHelper (libsu commands)
│   │   │   ├── service/     # TunnelForegroundService
│   │   │   ├── tunnel/      # TunnelClient (WebSocket/TCP Multiplexer)
│   │   │   └── ui/          # Jetpack Compose UI Screens & ViewModels
│   │   └── build.gradle.kts
│   ├── build.gradle.kts
│   └── settings.gradle.kts
├── relay-server/            # Relay Server trung gian
│   ├── src/
│   │   ├── server.ts        # Server core (TCP forwarder + Session manager)
│   │   └── session.ts       # Session handling
│   ├── Dockerfile
│   ├── docker-compose.yml
│   └── package.json
└── pc-tool/                 # Công cụ kết nối trên PC
    ├── connect.ps1          # Script PowerShell cho Windows
    ├── connect.sh           # Script Bash cho Linux / macOS
    └── src/cli.ts           # Optional Node.js CLI
```
