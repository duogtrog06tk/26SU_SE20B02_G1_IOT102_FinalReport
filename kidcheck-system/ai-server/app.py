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

# Khởi tạo 1 lần duy nhất khi server start — load known_faces và kết nối Firebase
# Không khởi tạo lại mỗi lần có request, vì encode ảnh tốn thời gian
face_service = FaceService()
firebase_service = FirebaseService()


@app.route("/recognize", methods=["POST"])
def recognize():
    """
    Endpoint chính — ESP32-CAM gọi vào đây mỗi khi chụp được ảnh.

    Request mong đợi (multipart/form-data):
        file: ảnh JPEG (field name phải là "file")

    Response JSON:
        Nhận ra:   {"status": "known", "student_id": "HS001_NguyenAn", "attendance_status": "present"}
        Không rõ:  {"status": "unknown"}
        Lỗi:       {"status": "error", "message": "..."}
    """
    try:
        if "file" not in request.files:
            logger.warning("Request không có field 'file'")
            return jsonify({"status": "error", "message": "Thiếu file ảnh trong request"}), 400

        file = request.files["file"]
        file_bytes = file.read()

        if len(file_bytes) == 0:
            return jsonify({"status": "error", "message": "File ảnh rỗng"}), 400

        # Đọc ảnh từ bytes bằng OpenCV, decode ra dạng numpy array
        np_array = np.frombuffer(file_bytes, np.uint8)
        image_bgr = cv2.imdecode(np_array, cv2.IMREAD_COLOR)

        if image_bgr is None:
            logger.warning("Không decode được ảnh — file có thể bị hỏng hoặc không phải ảnh hợp lệ")
            return jsonify({"status": "error", "message": "Không đọc được file ảnh"}), 400

        # face_recognition yêu cầu ảnh dạng RGB, nhưng OpenCV đọc ảnh dạng BGR
        # => phải convert trước khi đưa vào face_service
        image_rgb = cv2.cvtColor(image_bgr, cv2.COLOR_BGR2RGB)

        result = face_service.recognize(image_rgb)

        if not result["recognized"]:
             return jsonify({"status": "unknown", "success": False}), 200

        student_id = result["student_id"]

        if firebase_service.should_skip_duplicate(student_id):
             return jsonify({
                "status": "known", "success": True,
                "student_id": student_id, "name": student_id,
                "note": "duplicate_skipped"
            }), 200

        record = firebase_service.write_attendance(student_id)
        return jsonify({
            "status": "known", "success": True,
            "student_id": student_id, "name": student_id,
            "attendance_status": record["status"],
            "timestamp": record["timestamp"]
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


@app.route("/reload-faces", methods=["POST"])
def reload_faces():
    """
    Endpoint phụ — gọi tay khi vừa thêm học sinh mới vào known_faces/
    mà không muốn restart cả server.
    """
    face_service.load_known_faces()
    return jsonify({"status": "ok", "total_faces_loaded": len(face_service.known_names)}), 200


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
    logger.info(f"Thiết bị {device_id} online tại IP {device_ip}")
    try:
        firebase_service.save_device_status(device_id, device_ip, "ONLINE")
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