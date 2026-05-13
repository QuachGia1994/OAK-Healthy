import SwiftUI

/// Thành phần vẽ Logo OAK Healthy bằng SwiftUI Path.
/// Kết hợp hình ảnh chiếc lá cách điệu và chữ OAK.
public struct OAKLogoView: View {
    private let primaryColor = Color.green
    private let secondaryColor = Color.brown
    
    public init() {}
    
    public var body: some View {
        VStack(spacing: 8) {
            ZStack {
                // Hình chiếc lá cách điệu (Leaf Shape)
                Path { path in
                    path.move(to: CGPoint(x: 50, y: 10))
                    path.addQuadCurve(to: CGPoint(x: 10, y: 50), control: CGPoint(x: 10, y: 10))
                    path.addQuadCurve(to: CGPoint(x: 50, y: 90), control: CGPoint(x: 10, y: 90))
                    path.addQuadCurve(to: CGPoint(x: 90, y: 50), control: CGPoint(x: 90, y: 90))
                    path.addQuadCurve(to: CGPoint(x: 50, y: 10), control: CGPoint(x: 90, y: 10))
                    
                    // Gân lá
                    path.move(to: CGPoint(x: 50, y: 10))
                    path.addLine(to: CGPoint(x: 50, y: 90))
                }
                .stroke(primaryColor, lineWidth: 6)
                
                // Chữ O trong OAK lồng vào lá
                Text("O")
                    .font(.system(size: 30, weight: .black, design: .rounded))
                    .foregroundStyle(secondaryColor)
            }
            .frame(width: 100, height: 100)
            
            Text("OAK Healthy")
                .font(.system(size: 24, weight: .bold, design: .rounded))
                .foregroundStyle(primaryColor)
                .lineLimit(1)
                .fixedSize(horizontal: true, vertical: false)
                .minimumScaleFactor(0.5)
        }
    }
}

#Preview {
    OAKLogoView()
}
