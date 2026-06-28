import SwiftUI

struct SwippableTabView<Content: View>: View {
    @Binding var selectedTab: Int
    let tabs: [(title: String, icon: String)]
    @ViewBuilder let content: (Int) -> Content

    var body: some View {
        VStack(spacing: 0) {
            TabView(selection: $selectedTab) {
                ForEach(0..<tabs.count, id: \.self) { index in
                    content(index)
                        .tag(index)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
            .ignoresSafeArea(edges: .bottom)

            HStack(spacing: 0) {
                ForEach(0..<tabs.count, id: \.self) { index in
                    Button {
                        withAnimation(.snappy(duration: 0.25)) {
                            selectedTab = index
                        }
                    } label: {
                        VStack(spacing: 4) {
                            Image(systemName: tabs[index].icon)
                                .font(.system(size: 20, weight: .semibold))
                                .symbolRenderingMode(.hierarchical)
                            Text(tabs[index].title)
                                .font(.caption2.weight(.medium))
                        }
                        .foregroundStyle(selectedTab == index ? Color.accentColor : Color.secondary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                    }
                }
            }
            .background(.ultraThinMaterial)
        }
    }
}
