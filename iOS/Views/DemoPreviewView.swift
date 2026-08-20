import SwiftUI

#if DEBUG
public struct DemoPreviewView: View {
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    private let routines = [
        DemoRoutine(name: "Vitamin D3", detail: "08:00 • 1000 IU", statusKey: "taken"),
        DemoRoutine(name: "Creatine", detail: "12:30 • 5 g", statusKey: "dose_status_due"),
        DemoRoutine(name: "Magnesium", detail: "21:30 • 200 mg", statusKey: "dose_status_missed")
    ]

    public init() {}

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: OAKSpacing.section) {
                noticeSurface
                summarySurface
                routineSurface
            }
            .frame(maxWidth: 760)
            .padding(.horizontal, OAKSpacing.lg)
            .padding(.vertical, OAKSpacing.md)
            .frame(maxWidth: .infinity)
        }
        .background { Color.clear.oakBackground() }
        .navigationTitle("demo_preview_title".localized)
        .toolbarBackground(OAKPalette.background(for: colorScheme), for: .navigationBar)
        .toolbarBackground(.visible, for: .navigationBar)
    }

    private var noticeSurface: some View {
        VStack(alignment: .leading, spacing: OAKSpacing.sm) {
            Text("demo_preview_privacy_badge".localized)
                .font(.caption.weight(.semibold))
                .foregroundStyle(OAKPalette.accent)
            Text("demo_preview_client".localized)
                .font(.title3.weight(.semibold))
            Text("demo_preview_body".localized)
                .font(.footnote)
                .oakSecondaryText()
            Text("demo_preview_presentation_note".localized)
                .font(.caption)
                .oakSecondaryText()
        }
        .padding(OAKSpacing.xl)
        .frame(maxWidth: .infinity, alignment: .leading)
        .oakCardStyle(.paper, cornerRadius: OAKRadius.lg)
        .accessibilityElement(children: .combine)
    }

    private var summarySurface: some View {
        VStack(alignment: .leading, spacing: OAKSpacing.md) {
            Text("demo_preview_streak".localized)
                .font(.headline)
            OAKResponsiveMetricLayout {
                DemoMetric(key: "demo_preview_due")
                DemoMetric(key: "demo_preview_overdue")
                DemoMetric(key: "demo_preview_taken")
            }
        }
        .padding(OAKSpacing.xl)
        .frame(maxWidth: .infinity, alignment: .leading)
        .oakCardStyle(.paper, cornerRadius: OAKRadius.lg)
    }

    private var routineSurface: some View {
        VStack(spacing: 0) {
            ForEach(Array(routines.enumerated()), id: \.element.id) { index, routine in
                routineRow(routine)
                if index != routines.count - 1 {
                    Divider().overlay(OAKPalette.divider(for: colorScheme))
                }
            }
        }
        .padding(.horizontal, OAKSpacing.lg)
        .background(
            OAKPalette.surface(for: colorScheme),
            in: RoundedRectangle(cornerRadius: OAKRadius.md, style: .continuous)
        )
    }

    @ViewBuilder
    private func routineRow(_ routine: DemoRoutine) -> some View {
        if dynamicTypeSize >= .accessibility1 {
            VStack(alignment: .leading, spacing: OAKSpacing.xs) {
                routineLabel(routine)
                Text(routine.statusKey.localized)
                    .font(.caption.weight(.semibold))
            }
            .padding(.vertical, OAKSpacing.md)
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityElement(children: .combine)
        } else {
            HStack(spacing: OAKSpacing.md) {
                routineLabel(routine)
                Spacer()
                Text(routine.statusKey.localized)
                    .font(.caption.weight(.semibold))
            }
            .padding(.vertical, OAKSpacing.md)
            .accessibilityElement(children: .combine)
        }
    }

    private func routineLabel(_ routine: DemoRoutine) -> some View {
        VStack(alignment: .leading, spacing: OAKSpacing.xs) {
            Text(routine.name).font(.headline)
            Text(routine.detail).font(.caption).oakSecondaryText()
        }
    }
}

private struct DemoMetric: View {
    let key: String

    var body: some View {
        Text(key.localized)
            .font(.subheadline)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct DemoRoutine: Identifiable {
    var id: String { name }
    let name: String
    let detail: String
    let statusKey: String
}
#endif
