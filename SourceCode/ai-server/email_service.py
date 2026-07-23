import smtplib
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
from email.mime.image import MIMEImage
import base64
import os
import threading

def send_attendance_email_async(parent_email, student_name, time_str, base64_image, is_late, is_pickup):
    """
    Gửi email bất đồng bộ (tránh làm block luồng nhận diện AI chính)
    """
    thread = threading.Thread(
        target=_send_attendance_email,
        args=(parent_email, student_name, time_str, base64_image, is_late, is_pickup)
    )
    thread.daemon = True
    thread.start()

def _send_attendance_email(parent_email, student_name, time_str, base64_image, is_late, is_pickup):
    if not parent_email:
        return

    # Cấu hình SMTP
    SMTP_EMAIL = os.environ.get("SMTP_EMAIL", "")
    SMTP_PASSWORD = os.environ.get("SMTP_PASSWORD", "")
    
    # Tự động đọc từ file .env nếu có (không cần cài python-dotenv)
    env_path = os.path.join(os.path.dirname(__file__), '.env')
    if (not SMTP_EMAIL or not SMTP_PASSWORD) and os.path.exists(env_path):
        with open(env_path, 'r', encoding='utf-8') as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith('#') and '=' in line:
                    key, val = line.split('=', 1)
                    key = key.strip()
                    val = val.strip()
                    if key == 'SMTP_EMAIL': SMTP_EMAIL = val
                    if key == 'SMTP_PASSWORD': SMTP_PASSWORD = val

    if not SMTP_EMAIL or not SMTP_PASSWORD:
        print(f"⚠️ [Email] Chưa cấu hình SMTP_EMAIL hoặc SMTP_PASSWORD. Bỏ qua gửi email cho {parent_email}")
        return

    try:
        msg = MIMEMultipart('related')
        
        # Tiêu đề thư
        if is_pickup:
            subject = f"KidCheck: Bé {student_name} ĐÃ ĐƯỢC ĐÓN VỀ lúc {time_str}"
        elif is_late:
            subject = f"KidCheck: Bé {student_name} ĐÃ ĐẾN TRƯỜNG (Muộn) lúc {time_str}"
        else:
            subject = f"KidCheck: Bé {student_name} ĐÃ ĐẾN TRƯỜNG AN TOÀN lúc {time_str}"
            
        msg['Subject'] = subject
        msg['From'] = f"Hệ Thống KidCheck <{SMTP_EMAIL}>"
        msg['To'] = parent_email

        # Xử lý ảnh đính kèm (nếu có)
        image_cid = "kidcheck_image"
        has_image = False
        
        # Parse ảnh (nếu có) nhưng chưa attach vội
        img_attachment = None
        if base64_image and "base64," in base64_image:
            try:
                b64_data = base64_image.split(",")[1]
                image_data = base64.b64decode(b64_data)
                
                img_attachment = MIMEImage(image_data)
                img_attachment.add_header('Content-ID', f'<{image_cid}>')
                img_attachment.add_header('Content-Disposition', 'inline', filename='evidence.jpg')
                has_image = True
            except Exception as e:
                print(f"⚠️ [Email] Lỗi parse Base64 ảnh: {e}")

        # Nội dung HTML
        html_content = f"""
        <html>
        <head>
            <style>
                body {{ font-family: 'Quicksand', 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #fdf2f8; margin: 0; padding: 20px; }}
                .container {{ max-width: 550px; margin: 0 auto; background: #ffffff; border-radius: 24px; overflow: hidden; box-shadow: 0 10px 25px rgba(236, 72, 153, 0.1); border: 3px solid #fce7f3; }}
                .header {{ background: linear-gradient(135deg, #f472b6, #fb7185); color: white; padding: 25px 20px; text-align: center; border-radius: 20px 20px 0 0; }}
                .header h2 {{ margin: 0; font-size: 26px; font-weight: 800; letter-spacing: 0.5px; text-shadow: 1px 1px 2px rgba(0,0,0,0.1); }}
                .content {{ padding: 35px 30px; color: #475569; line-height: 1.7; font-size: 16px; }}
                .highlight {{ font-weight: 800; color: #db2777; font-size: 18px; }}
                .evidence-box {{ margin-top: 25px; text-align: center; padding: 20px; background: #fffbfef0; border-radius: 16px; border: 2px dashed #fbcfe8; }}
                .footer {{ background: #fdf2f8; padding: 20px; text-align: center; font-size: 13px; color: #94a3b8; border-top: 2px dashed #fce7f3; border-radius: 0 0 20px 20px; }}
                .greeting {{ font-size: 20px; font-weight: 700; color: #be185d; margin-bottom: 15px; }}
            </style>
        </head>
        <body>
            <div class="container">
                <div class="header">
                    <h2>🏫 KidCheck IOT 🧸</h2>
                </div>
                <div class="content">
                    <div class="greeting">Dạ, xin chào Ba Mẹ ạ! 👋</div>
                    <p>Hệ thống Camera AI thông minh của trường vừa ghi nhận bé <span class="highlight">{student_name}</span> 
                    {'<strong>đã được rước về nhà</strong> 🏡' if is_pickup else ('<strong>đã đến trường (hơi muộn một chút xíu)</strong> 🏃' if is_late else '<strong>đã tới trường ngoan ngoãn rồi ạ!</strong> 🥰')} 
                    vào lúc <span class="highlight">{time_str}</span>.</p>
        """

        if has_image:
            html_content += f"""
                    <div class="evidence-box">
                        <p style="margin-top: 0; margin-bottom: 15px; font-size: 15px; font-weight: 600; color: #db2777;">📸 Đây là chiếc ảnh xinh xắn của con lúc điểm danh nha:</p>
                        <img src="cid:{image_cid}" style="max-width: 100%; border-radius: 12px; box-shadow: 0 4px 12px rgba(219, 39, 119, 0.15);" alt="Bằng chứng">
                    </div>
            """
        else:
            html_content += """
                    <div class="evidence-box">
                        <p style="margin: 0; color: #94a3b8; font-style: italic;">(Rất tiếc hôm nay Camera chưa kịp bắt được khoảnh khắc của bé 🥺)</p>
                    </div>
            """

        html_content += """
                    <p style="margin-top: 30px;">Chúc Ba Mẹ một ngày làm việc thật nhiều năng lượng và niềm vui! ✨</p>
                    <p style="margin-bottom: 0;">Yêu thương,<br><strong style="color: #be185d; font-size: 18px;">Các cô giáo trường KidCheck ❤️</strong></p>
                </div>
                <div class="footer">
                    💌 Chiếc email này được gửi tự động bởi Hệ thống quản lý trường mầm non KidCheck IOT.<br>
                    Ba Mẹ vui lòng không trả lời trực tiếp thư này nhé!
                </div>
            </div>
        </body>
        </html>
        """

        # Đính kèm HTML ĐẦU TIÊN (bắt buộc theo chuẩn RFC multipart/related)
        msg.attach(MIMEText(html_content, 'html'))
        
        # Đính kèm ẢNH SAU HTML
        if img_attachment:
            msg.attach(img_attachment)

        # Kết nối SMTP Gmail (hoặc dịch vụ khác)
        server = smtplib.SMTP('smtp.gmail.com', 587)
        server.starttls()
        server.login(SMTP_EMAIL, SMTP_PASSWORD)
        
        text = msg.as_string()
        server.sendmail(SMTP_EMAIL, parent_email, text)
        server.quit()
        
        print(f"✅ [Email] Đã gửi thông báo thành công tới {parent_email} cho bé {student_name}")

    except Exception as e:
        print(f"❌ [Email] Lỗi khi gửi email tới {parent_email}: {e}")
