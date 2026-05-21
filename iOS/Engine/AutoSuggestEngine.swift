import Foundation

/// Các lỗi liên quan đến việc gợi ý chất bổ sung.
public enum SuggestionError: Error, Sendable {
    case searchFailed
}

/// Giao thức cho dịch vụ gợi ý tự động.
public protocol AutoSuggestService: Sendable {
    /// Tìm kiếm các gợi ý dựa trên từ khóa người dùng nhập.
    /// - Parameter query: Từ khóa tìm kiếm.
    /// - Returns: Danh sách các `SupplementReference` phù hợp.
    func fetchSuggestions(for query: String) async throws(SuggestionError) -> [SupplementReference]
}

/// Engine xử lý việc truy xuất dữ liệu từ từ điển cục bộ.
public struct SupplementAutoSuggester: AutoSuggestService {
    
    private let dictionary: [SupplementReference]
    
    public init(dictionary: [SupplementReference] = SupplementDictionary.references) {
        self.dictionary = dictionary
    }
    
    /// Thực hiện tìm kiếm bất đồng bộ trong từ điển.
    /// - Parameter query: Chuỗi tìm kiếm từ người dùng.
    /// - Returns: Mảng các gợi ý tìm thấy.
    public func fetchSuggestions(for query: String) async throws(SuggestionError) -> [SupplementReference] {
        // Trả về mảng rỗng nếu query quá ngắn để tối ưu hiệu năng
        guard query.count >= 2 else { return [] }
        
        let filtered = dictionary.filter { reference in
            reference.name.localizedCaseInsensitiveContains(query)
        }
        return filtered
    }
}
