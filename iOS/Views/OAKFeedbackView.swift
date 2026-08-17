import SwiftUI

struct OAKFeedbackView: View {
    let title: String
    let message: String
    var actionTitle: String?
    var action: (() -> Void)?

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title)
                .font(.headline)
            Text(message)
                .font(.subheadline)
                .foregroundStyle(.secondary)
            if let actionTitle, let action {
                Button(actionTitle, action: action)
                    .buttonStyle(.borderedProminent)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .oakCardStyle(.paper, cornerRadius: 14, strokeOpacity: 0.12, shadowOpacity: 0, shadowRadius: 0, shadowY: 0)
        .accessibilityElement(children: .contain)
    }
}
