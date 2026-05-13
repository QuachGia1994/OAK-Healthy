import SwiftUI

public struct UserGuideView: View {
    public init() {}
    
    public var body: some View {
        List {
            Section {
                Text("settings_guide_1".localized)
            } header: {
                Text("Mục 1: Thiết lập Stack")
            }
            
            Section {
                Text("settings_guide_2".localized)
            } header: {
                Text("Mục 2: Đồng bộ đa thiết bị")
            }
            
            Section {
                Text("settings_guide_3".localized)
            } header: {
                Text("Mục 3: Bảo mật tối cao")
            }
            
            Section {
                Text("settings_guide_4".localized)
            } header: {
                Text("Mục 4: Chẩn đoán thông báo")
            }
        }
        .navigationTitle("user_guide_title".localized)
    }
}

