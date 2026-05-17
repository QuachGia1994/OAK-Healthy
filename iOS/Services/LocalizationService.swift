import SwiftUI

public struct LocalizationService: Sendable {
    public static let shared = LocalizationService()
    
    private init() {}
    
    private var isVietnamese: Bool {
        guard let preferredLanguage = Locale.preferredLanguages.first else { return false }
        return preferredLanguage.hasPrefix("vi")
    }
    
    private let dictionary: [String: (en: String, vi: String)] = [
        "tab_home": ("Home", "Trang chủ"),
        "tab_history": ("History", "Lịch sử"),
        "tab_settings": ("Settings", "Cài đặt"),
        "dashboard_title": ("Dashboard", "Dashboard"),
        "history_title": ("History", "Lịch sử"),
        "intake_frequency_last_7": ("Intake Frequency (Last 7 days)", "Tần suất uống (7 ngày qua)"),
        "log_details": ("Log Details", "Chi tiết nhật ký"),
        "no_logs_yet": ("No logs yet.", "Chưa có nhật ký nào."),
        "today_intake_title": ("Today's Intake", "Cần uống hôm nay"),
        "no_intake_today": ("No intake scheduled for today.", "Không có lịch uống hôm nay."),
        "add_client_to_start": ("Add a Client to start.", "Thêm một học viên để bắt đầu."),
        "Add a Client to start.": ("Add a Client to start.", "Thêm một học viên để bắt đầu."),
        "resting_title": ("Resting", "Đang nghỉ"),
        "taken": ("Taken", "Đã uống"),
        "settings_title": ("Settings", "Cài đặt"),
        "client_management": ("Client Management", "Quản lý học viên"),
        "add_client": ("Add a Client", "Thêm học viên mới"),
        "edit_client": ("Edit Client", "Sửa học viên"),
        "add_supplement_title": ("Add Supplement", "Thêm chất mới"),
        "edit_supplement_title": ("Edit Supplement", "Chỉnh sửa"),
        "basic_info_title": ("Basic Info", "Thông tin cơ bản"),
        "schedule_cycle_title": ("Schedule & Cycle", "Lịch trình & Chu kỳ"),
        "name_hint": ("Name (e.g., Vitamin D3)", "Tên chất (VD: Vitamin D3)"),
        "dose_hint": ("Daily dose (e.g., 1000 IU)", "Liều lượng hàng ngày (VD: 1000 IU)"),
        "start_date": ("Start date", "Ngày bắt đầu"),
        "intake_time": ("Intake time", "Giờ uống"),
        "repeat_weekly": ("Repeat by weekdays", "Lặp theo thứ"),
        "repeat_on_weekdays": ("Repeat on", "Lặp vào"),
        "repeat_every": ("Every (weeks)", "Cách mỗi (tuần)"),
        "every_x_weeks_format": ("Every %d week(s)", "Mỗi %d tuần"),
        "example_on_days": ("e.g., 14", "Ví dụ: 14"),
        "example_off_days": ("e.g., 7", "Ví dụ: 7"),
        "save": ("Save", "Lưu"),
        "cancel": ("Cancel", "Hủy"),
        "delete": ("Delete", "Xóa"),
        "edit": ("Edit", "Chỉnh sửa"),
        "appearance_title": ("Appearance", "Giao diện"),
        "appearance_light": ("Light", "Sáng"),
        "appearance_dark": ("Dark", "Tối"),
        "appearance_system": ("System", "Hệ thống"),
        "my_list_title": ("My List", "Danh sách của tôi"),
        "manage_stack": ("Manage Stack", "Quản lý Stack"),
        "no_supplements_yet": ("No supplements yet.", "Chưa có thực phẩm bổ sung."),
        "user_guide_title": ("User Guide", "Hướng dẫn sử dụng"),
        "settings_guide_1": ("Tap (+) to add supplements. The app auto-calculates On/Off cycles from the start date.", "Nhấn (+) để thêm chất. App tự động tính chu kỳ On/Off (uống/nghỉ) dựa trên ngày bắt đầu."),
        "settings_guide_2": ("Enable Auto-Sync for realtime syncing across devices. Use a fixed Link Code to avoid re-entering.", "Bật \"Tự động đồng bộ\" để dữ liệu khớp nhau realtime giữa các máy. Sử dụng \"Mã liên kết\" cố định để không cần nhập lại nhiều lần."),
        "settings_guide_3": ("Use the eye icon to hide the code in public. Use Revoke to permanently delete cloud data.", "Nhấn biểu tượng \"Con mắt\" để ẩn mã khi quay màn hình hoặc ở nơi công cộng. Dùng nút \"Thu hồi mã\" để xóa vĩnh viễn dữ liệu trên Cloud khi không còn nhu cầu sync."),
        "settings_guide_4": ("Check Notification List to confirm the app sent schedules. Ensure Battery Optimization (Android) / Focus (iOS) isn’t blocking.", "Nếu lỡ mốc giờ, vào \"Kiểm tra danh sách\" để xem app đã đặt lệnh cho hệ điều hành chưa. Đảm bảo đã tắt \"Tối ưu hóa pin\" (Android) hoặc \"Không làm phiền\" (iOS)."),
        "about_title": ("About", "Giới thiệu"),
        "settings_about_body": ("OAK Healthy - Professional supplement tracker for Traders and Athletes.", "OAK Healthy - Trợ lý quản lý thực phẩm bổ sung chuyên nghiệp dành cho Trader và Vận động viên."),
        "copyright_title": ("Copyright & Author", "Bản quyền & Tác giả"),
        "settings_app_name_label": ("OAK Healthy v1.0", "Tên app: OAK Healthy v1.0"),
        "settings_author_label": ("Mr. Phong (Personal Trader)", "Tác giả: Mr. Phong (Personal Trader)"),
        "settings_copyright_body": ("Copyright © 2026 OAK Healthy. All rights reserved.", "Copyright © 2026 OAK Healthy. Mọi bản quyền được bảo lưu."),
        "export_data": ("Export Data", "Xuất dữ liệu"),
        "import_data": ("Import Data", "Nhập dữ liệu"),
        "share_stack": ("Share Stack", "Chia sẻ danh sách"),
        "data_tools": ("Data", "Dữ liệu"),
        "error_title": ("Error", "Lỗi"),
        "ok": ("OK", "OK"),
        "import_failed": ("Import failed.", "Nhập dữ liệu thất bại."),
        "export_failed": ("Export failed.", "Xuất dữ liệu thất bại."),
        "invalid_json": ("Invalid JSON file.", "File JSON không hợp lệ."),
        "missing_active_client": ("Please select a client first.", "Vui lòng chọn học viên trước."),
        "supplement_advice_vitamin_d3": ("Take with a fat-containing meal for better absorption.", "Uống cùng bữa ăn có chất béo để hấp thụ tốt hơn."),
        "supplement_advice_ashwagandha": ("Supports stress reduction and sleep quality.", "Hỗ trợ giảm stress và cải thiện chất lượng giấc ngủ."),
        "supplement_advice_boron": ("Cycle it to support hormone optimization.", "Nên dùng theo chu kỳ để hỗ trợ tối ưu hormone."),
        "supplement_advice_magnesium": ("Supports muscles and nervous system. Take before sleep.", "Uống 30 phút trước khi đi ngủ."),
        "supplement_advice_caffeine": ("Boosts focus and energy for workouts and trading sessions. Take ~30 minutes pre-workout.", "Tăng tập trung và năng lượng trước khi tập hoặc giao dịch. Uống khoảng 30 phút trước khi tập."),
        "supplement_advice_omega_3": ("Take after a fatty meal for best absorption.", "Nên uống sau bữa ăn có chất béo để hấp thụ tốt nhất."),
        "supplement_advice_coq10": ("Supports cellular energy. Take after a meal.", "Hỗ trợ năng lượng tế bào. Nên uống sau bữa ăn."),
        "supplement_advice_creatine": ("Supports strength and performance. Take daily.", "Hỗ trợ sức mạnh và hiệu suất. Uống mỗi ngày để duy trì."),
        "supplement_advice_zinc": ("Avoid taking on an empty stomach.", "Tránh uống khi bụng đói."),
        "supplement_advice_nac": ("Take near bedtime if it suits you. Avoid taking too late if it disrupts sleep.", "Gợi ý: uống gần giờ đi ngủ (trước khi ngủ khoảng 30–60 phút). Nếu bạn nhạy cảm, tránh uống quá muộn."),
        "update_available_title": ("Update Available", "Có bản cập nhật mới"),
        "update_available_message_format": ("Update to get the latest features and security improvements (v%@).", "Hãy cập nhật để trải nghiệm những tính năng mới nhất và tăng cường bảo mật (v%@)."),
        "update_now": ("Update Now", "Cập nhật ngay"),
        "later": ("Later", "Để sau"),
        "update_description": ("Update Coach Mode, Liquid Glass UI, and data synchronization.", "Cập nhật tính năng Quản lý học viên (Coach Mode), tối ưu giao diện Liquid Glass và đồng bộ dữ liệu."),
        "dedication_text": ("With all dedication and research in the fitness & trading journey.", "Bằng cả tâm huyết và nghiên cứu trong hành trình gym & trading."),
        "factory_reset": ("Factory Reset", "Khôi phục cài đặt gốc"),
        "wipe_data_warning": ("This will permanently delete all clients and logs. Continue?", "Hành động này sẽ xóa toàn bộ dữ liệu học viên và nhật ký. Tiếp tục?"),
        "not_available": ("N/A", "N/A"),
        "dose_format": ("Dose: %@", "Liều lượng: %@"),
        "days_remaining_format": ("%d days left", "Còn %d ngày"),
        "cycle_status_on": ("In cycle", "Đang trong chu kỳ"),
        "cycle_status_off": ("In rest", "Đang trong kỳ nghỉ"),
        "cycle_continuous": ("Continuous", "Uống liên tục"),
        "cycle_summary_format": ("%@, %d on / %d off", "%@, %d ngày uống / %d ngày nghỉ"),
        "continuous": ("Continuous", "Uống liên tục"),
        "on_days": ("On Days", "Số ngày uống"),
        "off_days": ("Off Days", "Số ngày nghỉ"),
        "duration": ("Duration", "Tổng thời hạn"),
        "months": ("Months", "Tháng"),
        "unlimited": ("Unlimited", "Vô thời hạn"),
        "suggested_format": ("Suggested: %@", "Gợi ý: %@"),
        "notification_title": ("Time to take it! 🌿", "Đến giờ uống rồi! 🌿"),
        "notification_body_format": ("You need to take %1$@ - Dose: %2$@. Wishing you a productive work/trading session!", "Bạn cần nạp %1$@ - Liều lượng: %2$@. Chúc bạn một phiên giao dịch/làm việc hiệu quả!"),
        "chart_axis_day": ("Day", "Ngày"),
        "chart_axis_count": ("Count", "Số lần")
    ]
    
    public func string(for key: String) -> String {
        guard let translation = dictionary[key] else { return key }
        return isVietnamese ? translation.vi : translation.en
    }
}

public extension String {
    var localized: String {
        LocalizationService.shared.string(for: self)
    }
}
