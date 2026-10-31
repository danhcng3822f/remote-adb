# Hướng Dẫn Sử Dụng Chi Tiết (Remote ADB Usage Guide)

## 1. Yêu Cầu Thiết Bị
- Thiết bị Android đã được Root bằng một trong các giải pháp:
  - **Magisk** (khuyến nghị phiên bản 24.0+)
  - **KernelSU**
  - **APatch**
  - **SuperSU** / Generic `su`
- Android 8.0 (API level 26) trở lên.

---

## 2. Các Bước Vận Hành Chi Tiết

### 2.1. Cấp Quyền Root Cho App
- Khi mở ứng dụng `Remote ADB` lần đầu tiên, hệ thống root (Magisk/KernelSU) sẽ hiển thị hộp thoại yêu cầu cấp quyền Superuser.
- Bấm **Grant** (hoặc Luôn Cho Phép).
- Banner trên đầu ứng dụng sẽ chuyển sang màu xanh lá: **"Root Access Granted"** kèm thông tin phiên bản root.

### 2.2. Kích Hoạt ADB TCP/IP Không Cần Cáp
- Bật switch **ADB Daemon (TCP:5555)** trên màn hình Home.
- Ứng dụng sẽ thực thi lệnh shell root ngầm:
  ```bash
  setprop service.adb.tcp.port 5555
  stop adbd
  start adbd
  ```
- Trạng thái sẽ báo `Active and listening on 127.0.0.1:5555`.

### 2.3. Bỏ Qua Popup "Allow USB Debugging" (Bypass Auth)
Bình thường khi kết nối ADB từ xa lần đầu, Android sẽ hiện popup hỏi xác nhận PC có dấu vân tay RSA RSA fingerprint. Để bỏ qua hoàn toàn bước này:
1. Mở terminal trên PC:
   ```bash
   # Cách 1: Sử dụng node CLI
   npx . show-key
   
   # Cách 2: Xem trực tiếp file public key
   cat ~/.android/adbkey.pub
   # hoặc trên Windows PowerShell:
   Get-Content $HOME\.android\adbkey.pub
   ```
2. Mở tab **PC Keys** trên app Android -> Bấm nút **+** -> Dán nội dung key vào -> Bấm **Add Key**.
3. Khóa sẽ được ghi trực tiếp vào `/data/misc/adb/adb_keys` với phân quyền `0640 system:shell`. Từ thời điểm này, PC của bạn được tin cậy vĩnh viễn.

### 2.4. Khởi Động Reverse Tunnel Qua Internet
1. Vào tab **Settings** -> Nhập địa chỉ Relay Server VPS của bạn (hoặc giữ mặc định nếu server nội bộ).
2. Bấm **Save All Settings**.
3. Quay lại tab **Home** -> Bấm **Start Tunnel Relay**.
4. Ứng dụng sẽ chạy dịch vụ Foreground Service, duy trì kết nối WebSocket tới Server.
5. Server sẽ cấp phát cổng TCP (ví dụ `15000`).

### 2.5. Kết Nối & Điều Khiển Từ Máy Tính
Trên PC (ở bất cứ đâu có mạng Internet):
```powershell
# Windows
adb connect relay.yourdomain.com:15000

# Kiểm tra thiết bị
adb devices

# Chạy lệnh shell
adb shell

# Hoặc mở scrcpy điều khiển màn hình
scrcpy -s relay.yourdomain.com:15000
```
