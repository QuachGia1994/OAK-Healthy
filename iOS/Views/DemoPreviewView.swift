import SwiftUI

#if DEBUG
public struct DemoPreviewView: View {
    private let routines = [
        DemoRoutine(name: "Vitamin D3", detail: "08:00 • 1000 IU", statusKey: "taken"),
        DemoRoutine(name: "Creatine", detail: "12:30 • 5 g", statusKey: "dose_status_due"),
        DemoRoutine(name: "Magnesium", detail: "21:30 • 200 mg", statusKey: "dose_status_missed")
    ]

    public init() {}

    public var body: some View {
        List {
            Section {
                Text("demo_preview_body".localized)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            Section("demo_preview_client".localized) {
                HStack {
                    Text("demo_preview_streak".localized).fontWeight(.semibold)
                    Spacer()
                    Text("demo_preview_due".localized)
                    Text("demo_preview_overdue".localized)
                    Text("demo_preview_taken".localized)
                }
            }
            Section("today_intake_title".localized) {
                ForEach(routines) { routine in
                    HStack {
                        VStack(alignment: .leading, spacing: 3) {
                            Text(routine.name).font(.headline)
                            Text(routine.detail).font(.caption).foregroundStyle(.secondary)
                        }
                        Spacer()
                        Text(routine.statusKey.localized)
                    }
                    .accessibilityElement(children: .combine)
                }
            }
        }
        .navigationTitle("demo_preview_title".localized)
    }
}

private struct DemoRoutine: Identifiable {
    var id: String { name }
    let name: String
    let detail: String
    let statusKey: String
}
#endif
