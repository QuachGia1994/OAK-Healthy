import SwiftUI

public struct OAKLogoView: View {
    public init() {}
    
    public var body: some View {
        VStack(spacing: 8) {
            ZStack {
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .fill(
                        LinearGradient(
                            colors: [Color(red: 0.13, green: 0.90, blue: 0.56), Color(red: 0.02, green: 0.34, blue: 0.29)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                
                Circle()
                    .fill(Color(red: 0.96, green: 1.00, blue: 0.99).opacity(0.98))
                    .frame(width: 54, height: 54)
                    .offset(x: -8, y: 6)
                    .shadow(color: Color.black.opacity(0.18), radius: 6, x: 0, y: 4)
                
                Path { p in
                    p.move(to: CGPoint(x: 44, y: 20))
                    p.addLine(to: CGPoint(x: 56, y: 20))
                    p.addLine(to: CGPoint(x: 52, y: 10))
                    p.addLine(to: CGPoint(x: 48, y: 10))
                    p.closeSubpath()
                }
                .fill(Color(red: 0.90, green: 1.00, blue: 0.96).opacity(0.90))
                .offset(x: -8, y: 4)
                
                Capsule()
                    .fill(Color(red: 0.75, green: 0.97, blue: 0.88).opacity(0.98))
                    .frame(width: 66, height: 38)
                    .offset(x: 14, y: 8)
                    .shadow(color: Color.black.opacity(0.15), radius: 6, x: 0, y: 4)
                
                RoundedRectangle(cornerRadius: 2.5, style: .continuous)
                    .fill(Color.white.opacity(0.30))
                    .frame(width: 6, height: 76)
                    .offset(x: 6, y: 8)
            }
            .frame(width: 100, height: 100)
            
            Text("app_name".localized)
                .font(.system(size: 24, weight: .bold, design: .rounded))
                .foregroundStyle(Color(red: 0.13, green: 0.90, blue: 0.56))
                .lineLimit(1)
                .fixedSize(horizontal: true, vertical: false)
                .minimumScaleFactor(0.5)
        }
    }
}

#Preview {
    OAKLogoView()
}
