import SwiftUI

struct OAKAdaptiveMetrics: Sendable {
    let compactWidth: Bool
    let largeText: Bool
    let horizontalPadding: CGFloat
    let stackMetrics: Bool
}

private struct OAKAdaptiveMetricsKey: EnvironmentKey {
    static let defaultValue = OAKAdaptiveMetrics(
        compactWidth: false,
        largeText: false,
        horizontalPadding: 16,
        stackMetrics: false
    )
}

extension EnvironmentValues {
    var oakAdaptiveMetrics: OAKAdaptiveMetrics {
        get { self[OAKAdaptiveMetricsKey.self] }
        set { self[OAKAdaptiveMetricsKey.self] = newValue }
    }
}

private struct OAKAdaptiveMetricsModifier: ViewModifier {
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    func body(content: Content) -> some View {
        GeometryReader { proxy in
            let compact = proxy.size.width <= 360
            let metrics = OAKAdaptiveMetrics(
                compactWidth: compact,
                largeText: dynamicTypeSize >= .accessibility1,
                horizontalPadding: compact ? 12 : proxy.size.width >= 700 ? 28 : 16,
                stackMetrics: compact || dynamicTypeSize >= .accessibility1
            )
            content.environment(\.oakAdaptiveMetrics, metrics)
        }
    }
}

extension View {
    func oakAdaptiveRoot() -> some View {
        modifier(OAKAdaptiveMetricsModifier())
    }
}

struct OAKResponsiveMetricLayout<Content: View>: View {
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    private let content: Content

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        ViewThatFits(in: .horizontal) {
            if dynamicTypeSize < .accessibility1 {
                HStack(spacing: 12) { content }
            }
            VStack(alignment: .leading, spacing: 10) { content }
        }
    }
}
