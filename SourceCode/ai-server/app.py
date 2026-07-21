"""
app.py
Flask server — điểm vào chính của AI server.
Chỉ lo việc nhận request HTTP và trả response, KHÔNG chứa logic AI hay
Firebase trực tiếp — toàn bộ logic đó nằm ở face_service.py và firebase_service.py.

Cách chạy:
    python app.py
Server sẽ chạy ở http://0.0.0.0:5000
"""

import logging
import cv2
import numpy as np
from flask import Flask, request, jsonify
from flask_cors import CORS  # FIX #2: Cho phép Web App gọi API qua trình duyệt không bị chặn CORS

from face_service import FaceService
from firebase_service import FirebaseService

# Cấu hình logging — in ra console kèm thời gian, để dễ debug khi demo
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    datefmt="%H:%M:%S"
)
logger = logging.getLogger("app")

app = Flask(__name__)
CORS(app)  # FIX #2: Áp dụng CORS cho toàn bộ Flask app

# Khởi tạo 1 lần duy nhất khi server start — load known_faces và kết nối Firebase
# Không khởi tạo lại mỗi lần có request, vì encode ảnh tốn thời gian
firebase_service = FirebaseService()

# Tự động đồng bộ ảnh học sinh từ Firebase về thư mục local khi khởi động
try:
    from config import KNOWN_FACES_DIR
    firebase_service.sync_student_photos(KNOWN_FACES_DIR)
except Exception as e:
    logger.error(f"Lỗi đồng bộ ảnh học sinh ban đầu: {e}")

face_service = FaceService()


def format_student_name(student_id):
    """
    Chuẩn hóa Mã số học sinh phản hồi (Ví dụ: "HS001" hoặc "HS001_NguyenAn" -> "HS001 OK!")
    """
    if "_" in student_id:
        code = student_id.split("_")[0].strip()
    else:
        code = student_id.strip()
    return f"{code} OK!"


@app.route("/recognize", methods=["POST"])
def recognize():
    """
    Endpoint chính — ESP32-CAM gọi vào đây mỗi khi chụp được ảnh.
    """
    try:
        if "file" in request.files:
            file = request.files["file"]
            file_bytes = file.read()
        elif request.data:
            file_bytes = request.data
        else:
            logger.warning("Request missing image data")
            return jsonify({"status": "error", "message": "Missing image file"}), 400

        if len(file_bytes) == 0:
            return jsonify({"status": "error", "message": "Empty image data"}), 400

        np_array = np.frombuffer(file_bytes, np.uint8)
        image_bgr = cv2.imdecode(np_array, cv2.IMREAD_COLOR)

        if image_bgr is None:
            logger.warning("Image decode failed")
            return jsonify({"status": "error", "message": "Invalid image file"}), 400

        image_rgb = cv2.cvtColor(image_bgr, cv2.COLOR_BGR2RGB)

        result = face_service.recognize(image_rgb)

        if not result["recognized"]:
             return jsonify({"status": "unknown", "success": False}), 200

        student_id = result["student_id"]
        name_ok = format_student_name(student_id)

        if firebase_service.should_skip_duplicate(student_id):
             return jsonify({
                "status": "known", "success": True,
                "student_id": student_id, "name": name_ok,
                "note": "duplicate_skipped"
            }), 200

        # Encode ảnh captured từ ESP32-CAM thành Data URL compact để truyền tức thì tới WebApp
        try:
            h, w = image_bgr.shape[:2]
            if w > 360:
                scale = 360.0 / w
                resized = cv2.resize(image_bgr, (360, int(h * scale)))
            else:
                resized = image_bgr
            _, buffer = cv2.imencode(".jpg", resized, [cv2.IMWRITE_JPEG_QUALITY, 75])
            import base64
            captured_b64 = "data:image/jpeg;base64," + base64.b64encode(buffer).decode("utf-8")
        except Exception:
            captured_b64 = None

        record = firebase_service.write_attendance(student_id, captured_b64)
        return jsonify({
            "status": "known", "success": True,
            "student_id": student_id, "name": name_ok,
            "attendance_status": record["status"],
            "timestamp": record["timestamp"],
            "captured_photo": captured_b64
        }), 200

    except Exception as e:
        logger.exception("Lỗi không mong muốn trong /recognize")
        return jsonify({"status": "error", "success": False, "message": str(e)}), 500


@app.route("/register", methods=["POST"])
def register():
    """
    Endpoint riêng cho luồng "Đăng ký từ xa" — khác /recognize vì đây là
    THÊM ảnh mới vào known_faces/, không phải so sánh để điểm danh.

    Gọi bởi: ESP32-CAM, sau khi nó polling thấy lệnh "capture_request" từ
    Firebase (do Spring Boot ghi vào khi admin bấm nút "Chụp" trên web).

    Request mong đợi (multipart/form-data):
        file: ảnh JPEG (field name "file")
        student_id: mã học sinh (field name "student_id", ví dụ "HS010_VuGiang")

    Response JSON:
        Thành công: {"status": "ok", "photo_count": 3}
        Lỗi ảnh:    {"status": "error", "message": "Không phát hiện khuôn mặt..."}
    """
    try:
        if "file" not in request.files:
            return jsonify({"status": "error", "message": "Thiếu file ảnh trong request"}), 400

        student_id = request.form.get("student_id")
        if not student_id:
            return jsonify({"status": "error", "message": "Thiếu student_id trong request"}), 400

        file = request.files["file"]
        file_bytes = file.read()
        np_array = np.frombuffer(file_bytes, np.uint8)
        image_bgr = cv2.imdecode(np_array, cv2.IMREAD_COLOR)

        if image_bgr is None:
            return jsonify({"status": "error", "message": "Không đọc được file ảnh"}), 400

        image_rgb = cv2.cvtColor(image_bgr, cv2.COLOR_BGR2RGB)

        result = face_service.save_new_face(student_id, image_rgb)

        if not result["success"]:
            logger.warning(f"Đăng ký thất bại cho {student_id}: {result['error']}")
            return jsonify({"status": "error", "message": result["error"]}), 400

        logger.info(f"Đăng ký thành công cho {student_id} — ảnh số {result['photo_count']}")
        return jsonify({"status": "ok", "photo_count": result["photo_count"]}), 200

    except Exception as e:
        logger.exception("Lỗi không mong muốn trong /register")
        return jsonify({"status": "error", "message": str(e)}), 500


@app.route("/control-device", methods=["POST"])
def control_device():
    """
    STAGE 1: Web App gọi lên đây để yêu cầu ESP32-CAM chụp 3 ảnh.
    AI Server gửi lệnh GET đến ESP32-CAM, nhận 3 ảnh nhị phân multipart trả về,
    mã hóa thành Base64 Data URL và gửi trực tiếp về WebApp để hiển thị xem trước.
    (Chưa lưu vào known_faces/ cho đến khi người dùng bấm Lưu thông tin).
    """
    try:
        import requests as py_requests
        from firebase_admin import db
        import base64

        data = request.get_json(silent=True) or {}
        student_id = data.get("student_id")
        student_name = data.get("name", student_id)
        if not student_id:
            return jsonify({"status": "error", "message": "Thiếu student_id"}), 400

        # 1. Lấy thông tin trạng thái & IP của ESP32-CAM từ Firebase
        esp_status = None
        if firebase_service.use_rest_fallback:
            try:
                r = py_requests.get(f"{firebase_service.database_url}System/ESP32_CAM_Status.json", timeout=5)
                if r.status_code == 200:
                    esp_status = r.json()
            except Exception as e:
                logger.error(f"Lỗi REST đọc IP ESP: {e}")
        else:
            try:
                esp_status = db.reference("System/ESP32_CAM_Status").get()
            except Exception as e:
                logger.warning(f"Lỗi SDK đọc IP ESP: {e}. Chuyển sang REST...")
                try:
                    r = py_requests.get(f"{firebase_service.database_url}System/ESP32_CAM_Status.json", timeout=5)
                    esp_status = r.json() if r.status_code == 200 else None
                except:
                    pass
                    
        if not esp_status or not esp_status.get("ip"):
            return jsonify({"status": "error", "message": "Thiếu thông tin kết nối ESP32-CAM hoặc thiết bị đang ngoại tuyến!"}), 400
            
        esp_ip = esp_status.get("ip")
        
        # 2. Gửi lệnh HTTP GET xuống endpoint /capture-reg của ESP32-CAM để yêu cầu chụp 3 ảnh
        logger.info(f"Đang gửi yêu cầu chụp ảnh cho {student_id} tới ESP32-CAM tại http://{esp_ip}/capture-reg")
        esp_url = f"http://{esp_ip}/capture-reg?student_id={student_id}"
        
        try:
            esp_res = py_requests.get(esp_url, timeout=20)
        except Exception as e:
            return jsonify({"status": "error", "message": f"Không thể kết nối đến ESP32-CAM tại IP {esp_ip}: {e}"}), 400
            
        if esp_res.status_code != 200:
            return jsonify({"status": "error", "message": f"ESP32-CAM trả về lỗi: {esp_res.status_code}"}), 400

        # 3. Tiếp nhận ảnh từ multipart stream và mã hóa thành Base64 Data URL
        boundary = "KidCheckBoundary7MA4YWxk"
        parts = esp_res.content.split(b"--" + boundary.encode())
        
        b64_images = []
        for part in parts:
            if b"image/jpeg" in part:
                header_end = part.find(b"\r\n\r\n")
                if header_end != -1:
                    img_data = part[header_end + 4:]
                    if img_data.endswith(b"\r\n"):
                        img_data = img_data[:-2]
                    elif img_data.endswith(b"\r"):
                        img_data = img_data[:-1]
                        
                    np_array = np.frombuffer(img_data, np.uint8)
                    img_bgr = cv2.imdecode(np_array, cv2.IMREAD_COLOR)
                    if img_bgr is not None:
                        # Nén kích thước vừa phải để truyền tức thì qua JSON
                        h, w = img_bgr.shape[:2]
                        if w > 480:
                            scale = 480.0 / w
                            img_bgr = cv2.resize(img_bgr, (480, int(h * scale)))
                        _, buf = cv2.imencode(".jpg", img_bgr, [cv2.IMWRITE_JPEG_QUALITY, 80])
                        b64_str = "data:image/jpeg;base64," + base64.b64encode(buf).decode("utf-8")
                        b64_images.append(b64_str)

        if len(b64_images) < 3:
            return jsonify({
                "status": "error", 
                "message": f"ESP32-CAM không chụp đủ 3 ảnh (chỉ nhận được {len(b64_images)} ảnh hợp lệ)"
            }), 400

        logger.info(f"Đã nhận 3 ảnh từ ESP32-CAM cho {student_id}, gửi về WebApp xem trước.")
        return jsonify({
            "success": True,
            "status": "ok",
            "photos_sent": len(b64_images),
            "student_id": student_id,
            "images": b64_images,
            "preview_url": b64_images[0]
        }), 200
    except Exception as e:
        logger.exception("Lỗi không mong muốn trong /control-device")
        return jsonify({"status": "error", "message": str(e)}), 500


@app.route("/verify-and-register", methods=["POST"])
def verify_and_register():
    """
    STAGE 2: Web App gửi 3 ảnh chụp Base64 lên đây khi bấm 'Lưu thông tin'.
    AI Server kiểm duyệt khuôn mặt bằng face_recognition trên TOÀN BỘ các ảnh:
    - Nếu có bất kỳ ảnh nào không chứa khuôn mặt -> Trả về lỗi 400, YÊU CẦU CHỤP LẠI.
    - Nếu tất cả ảnh hợp lệ -> Lưu vào known_faces/<student_id>/, mã hóa encodings vào RAM và cập nhật Firebase.
    """
    try:
        import os
        import shutil
        import base64
        import requests as py_requests
        import face_recognition
        from firebase_admin import db
        from config import KNOWN_FACES_DIR

        data = request.get_json(silent=True) or {}
        student_id = data.get("student_id")
        student_name = data.get("name", student_id)
        b64_images = data.get("images") or []

        if not student_id or not b64_images:
            return jsonify({"status": "error", "message": "Thiếu student_id hoặc danh sách ảnh chụp!"}), 400

        images_rgb = []
        images_bgr = []

        # Decode danh sách Base64 images
        for idx, b64_item in enumerate(b64_images):
            if "," in b64_item:
                _, encoded = b64_item.split(",", 1)
            else:
                encoded = b64_item
            file_bytes = base64.b64decode(encoded)
            np_array = np.frombuffer(file_bytes, np.uint8)
            img_bgr = cv2.imdecode(np_array, cv2.IMREAD_COLOR)

            if img_bgr is None:
                return jsonify({"status": "error", "message": f"Không giải mã được ảnh thứ {idx+1}!"}), 400

            img_rgb = cv2.cvtColor(img_bgr, cv2.COLOR_BGR2RGB)
            images_rgb.append(img_rgb)
            images_bgr.append(img_bgr)

        # KIỂM DUYỆT KHUÔN MẶT CÁN BỘ AI: Duyệt tất cả các ảnh
        for i, img_rgb in enumerate(images_rgb):
            face_locations = face_recognition.face_locations(img_rgb)
            if len(face_locations) == 0:
                logger.warning(f"Thẩm định thất bại: Ảnh thứ {i+1} của {student_id} không chứa khuôn mặt.")
                return jsonify({
                    "success": False,
                    "status": "error",
                    "message": f"Ảnh thứ {i+1} chụp từ ESP32-CAM không phát hiện được khuôn mặt rõ nét! Vui lòng yêu cầu bé đứng thẳng trước camera và chụp lại."
                }), 400

        # NẾU TẤT CẢ ẢNH HỢP LỆ -> Lưu vào known_faces/ và cập nhật model AI RAM
        student_dir = os.path.join(KNOWN_FACES_DIR, student_id)
        if os.path.exists(student_dir):
            shutil.rmtree(student_dir)
        os.makedirs(student_dir, exist_ok=True)

        for idx, img_bgr in enumerate(images_bgr):
            photo_path = os.path.join(student_dir, f"{idx+1}.jpg")
            cv2.imwrite(photo_path, img_bgr)

            img_rgb = images_rgb[idx]
            face_locations = face_recognition.face_locations(img_rgb)
            new_encoding = face_recognition.face_encodings(img_rgb, face_locations)[0]
            face_service.known_encodings.append(new_encoding)
            face_service.known_names.append(student_id)

        local_ip = get_local_ip()
        new_photo_url = f"http://{local_ip}:5000/faces/{student_id}"

        student_payload = {
            "id": student_id,
            "name": student_name,
            "photo": new_photo_url
        }

        if firebase_service.use_rest_fallback:
            try:
                py_requests.patch(
                    f"{firebase_service.database_url}students/{student_id}.json",
                    json=student_payload,
                    timeout=5
                )
            except Exception as e:
                logger.error(f"Lỗi REST ghi student payload: {e}")
        else:
            try:
                db.reference(f"students/{student_id}").update(student_payload)
            except Exception as e:
                logger.warning(f"Lỗi SDK ghi student payload ({e}). Thử REST...")
                try:
                    py_requests.patch(
                        f"{firebase_service.database_url}students/{student_id}.json",
                        json=student_payload,
                        timeout=5
                    )
                except Exception as e2:
                    logger.error(f"REST fallback lỗi: {e2}")

        logger.info(f"Đã kiểm duyệt & nạp mẫu thành công cho {student_id} ({len(images_rgb)} ảnh).")
        return jsonify({
            "success": True,
            "status": "ok",
            "message": f"Đã kiểm duyệt & nạp mẫu khuôn mặt AI thành công ({len(images_rgb)} ảnh)!",
            "photo_url": new_photo_url
        }), 200
    except Exception as e:
        logger.exception("Lỗi không mong muốn trong /verify-and-register")
        return jsonify({"status": "error", "message": str(e)}), 500


@app.route("/reload-faces", methods=["POST"])
def reload_faces():
    """
    Endpoint phụ — gọi tay khi vừa thêm học sinh mới vào known_faces/
    hoặc khi đồng bộ lại toàn bộ từ Firebase.
    """
    try:
        from config import KNOWN_FACES_DIR
        firebase_service.sync_student_photos(KNOWN_FACES_DIR)
    except Exception as e:
        logger.error(f"Lỗi đồng bộ ảnh khi reload: {e}")

    face_service.load_known_faces()
    return jsonify({"status": "ok", "total_faces_loaded": len(face_service.known_names)}), 200


@app.route("/faces/<student_id>", methods=["GET"])
def get_student_face(student_id):
    """Trả về ảnh khuôn mặt đầu tiên của học sinh phục vụ việc hiển thị trên Web App."""
    import os
    from flask import send_from_directory, abort
    from config import KNOWN_FACES_DIR
    student_dir = os.path.join(KNOWN_FACES_DIR, student_id)
    if not os.path.exists(student_dir):
        abort(404)
    files = sorted([f for f in os.listdir(student_dir) if f.lower().endswith((".jpg", ".jpeg", ".png"))])
    if not files:
        abort(404)
    return send_from_directory(student_dir, files[0])


@app.route("/health", methods=["GET"])
def health():
    """Endpoint kiểm tra server còn sống — dùng để test nhanh bằng browser."""
    return jsonify({
        "status": "running",
        "known_students": len(set(face_service.known_names))
    }), 200

import socket

@app.route("/device-boot", methods=["POST"])
def device_boot():
    """ESP32-CAM gọi khi vừa kết nối WiFi thành công, báo trạng thái online."""
    data = request.get_json(silent=True) or {}
    device_id = data.get("device_id", "unknown")
    device_ip = data.get("ip", "unknown")
    device_ssid = data.get("ssid", "unknown")
    device_rssi = data.get("rssi", 0)
    logger.info(f"Thiết bị {device_id} online tại IP {device_ip} | Wi-Fi: {device_ssid} ({device_rssi} dBm)")
    try:
        firebase_service.save_device_status(device_id, device_ip, "ONLINE", device_ssid, device_rssi)
    except Exception as e:
        logger.error(f"Lỗi lưu trạng thái thiết bị lên Firebase: {e}")
    return jsonify({"status": "ok"}), 200

def get_local_ip():
    """Lấy IP LAN thực của máy đang chạy ai-server (không phải 127.0.0.1)."""
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
    except Exception:
        ip = "127.0.0.1"
    finally:
        s.close()
    return ip
if __name__ == "__main__":
    logger.info("Đang khởi động KidCheck AI Server...")
    local_ip = get_local_ip()
    firebase_service.register_server_ip(local_ip, 5000)
    app.run(host="0.0.0.0", port=5000, debug=False)