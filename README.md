# KidCheck-IOT102

## ⚠️ Thiết lập bắt buộc trước khi chạy

### 1. File `serviceAccountKey.json` (Firebase Credential)
Web App Java Backend cần file khóa bí mật Firebase để kết nối Admin SDK.

**Bước thực hiện:**
1. Truy cập [Firebase Console](https://console.firebase.google.com) → Dự án `kidcheck-attendance-c5504`
2. Vào **Project Settings** → Tab **Service accounts** → Nhấn **Generate new private key**
3. Tải file JSON về và đổi tên thành `serviceAccountKey.json`
4. Đặt file vào **cả hai** vị trí sau:
   - `kidcheck-system/ai-server/serviceAccountKey.json` *(cho Python AI Server)*
   - `kidcheck-system/web/src/main/resources/serviceAccountKey.json` *(cho Java Web Backend)*

> **Lưu ý:** File này chứa khóa bí mật, đã được thêm vào `.gitignore`. **Không commit** file này lên GitHub.

---

# Code cơ bản


    Sửa code → add → commit → push

-Khởi tạo (initialize) một project thành Git repository:

    git init 

-Thêm toàn bộ file vào staging:

    git add . 

-Thêm file muốn thêm:

    git add <Tên File>

-Lưu trạng thái hiện tại với comment để miêu tả cho sự thay đổi:

    git commit -m "comment"

-code để link với repo trên github, link vào mới push được nha ae:

    git remote add origin https://github.com/duongtrong06tk-rgb/KidCheck-IOT102.git

-Đẩy code lên GitHub:

    git push -u origin main

-Lấy code mới từ GitHub:

    git pull origin main

-Kiểm tra trạng thái xem file nào đã sửa, file nào chưa commit:

    git status

-Xem lịch sử commit (Nhấn phím "q" để thoát log):

    git log
