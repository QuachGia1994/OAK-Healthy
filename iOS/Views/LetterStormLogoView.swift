import SwiftUI
import UIKit

public struct LetterStormLogoView: View {
    @Environment(\.colorScheme) private var colorScheme
    
    private struct Particle: Hashable {
        let char: Character
        let seedA: Double
        let seedB: Double
        let speed: Double
        let radius: Double
    }
    
    private let word: String
    private let particles: [Particle]
    private let targets: [CGPoint]
    private let duration: Double
    
    public init(
        word: String = "OAK HEALTHY",
        particleCount: Int = 180,
        duration: Double = 5.2
    ) {
        let w = word.trimmingCharacters(in: .whitespacesAndNewlines)
        self.word = w.isEmpty ? "OAK HEALTHY" : w
        self.duration = max(2.8, duration)
        self.targets = Self.buildWordTargets(word: self.word)
        
        var rng = SeededGenerator(seed: 421_337)
        let pool = Array(self.word.replacingOccurrences(of: " ", with: ""))
        let fallback = Array("OAKHEALTHY")
        self.particles = (0..<max(40, particleCount)).map { i in
            let chars = pool.isEmpty ? fallback : pool
            return Particle(
                char: chars[i % chars.count],
                seedA: Double.random(in: 0..<10_000, using: &rng),
                seedB: Double.random(in: 0..<10_000, using: &rng),
                speed: Double.random(in: 0.6...2.0, using: &rng),
                radius: Double.random(in: 0.12...0.54, using: &rng)
            )
        }
    }
    
    public var body: some View {
        TimelineView(.animation) { timeline in
            Canvas { context, size in
                let t = Self.loopFraction(time: timeline.date.timeIntervalSinceReferenceDate, duration: duration)
                
                let stormEnd = 0.56
                let alignEnd = 0.76
                let holdEnd = 0.88
                
                let alignProgress: Double = {
                    if t < stormEnd { return 0 }
                    if t < alignEnd { return Self.smoothstep((t - stormEnd) / (alignEnd - stormEnd)) }
                    if t < holdEnd { return 1 }
                    return 1 - Self.smoothstep((t - holdEnd) / (1 - holdEnd))
                }()
                
                let wordAlpha: Double = {
                    if t < stormEnd { return 0 }
                    if t < alignEnd { return Self.smoothstep((t - stormEnd) / (alignEnd - stormEnd)) }
                    if t < holdEnd { return 1 }
                    return 1 - Self.smoothstep((t - holdEnd) / (1 - holdEnd))
                }()
                
                let particleAlpha = 1 - (wordAlpha * 0.55)
                
                let center = CGPoint(x: size.width / 2, y: size.height / 2)
                let particleFontSize = max(9, min(size.width, size.height) * 0.03)
                let wordFontSize = max(22, min(size.width, size.height) * 0.17)
                
                let baseColor: Color = (colorScheme == .dark) ? .white : Color.black.opacity(0.86)
                let shadowColor: Color = (colorScheme == .dark) ? Color.black.opacity(0.5) : Color.white.opacity(0.55)
                
                for i in particles.indices {
                    let p = particles[i]
                    let angle = ((p.seedA * 0.001) + t * p.speed * 2.0) * (Double.pi * 2.0)
                    let wobble = 0.08 * sin((t * 7.0 + p.seedB * 0.0007) * (Double.pi * 2.0))
                    let r = min(0.55, max(0.05, p.radius + wobble))
                    
                    let stormX = cos(angle) * r
                    let stormY = sin(angle * 1.03) * r * 0.72
                    
                    let target = targets[i % targets.count]
                    let targetX = target.x * 0.92
                    let targetY = target.y * 0.30
                    
                    let xN = Self.lerp(stormX, targetX, alignProgress)
                    let yN = Self.lerp(stormY, targetY, alignProgress)
                    
                    let x = center.x + xN * size.width
                    let y = center.y + yN * size.height
                    
                    let text = Text(String(p.char))
                        .font(.system(size: particleFontSize, weight: .bold, design: .rounded))
                        .foregroundStyle(baseColor.opacity(particleAlpha * 0.9))
                    
                    context.draw(text, at: CGPoint(x: x, y: y), anchor: .center)
                }
                
                if wordAlpha > 0 {
                    let text = Text(word)
                        .font(.system(size: wordFontSize, weight: .heavy, design: .rounded))
                        .foregroundStyle(baseColor.opacity(wordAlpha))
                    context.drawLayer { layer in
                        layer.addFilter(.shadow(color: shadowColor, radius: 10, x: 0, y: 3))
                        layer.draw(text, at: center, anchor: .center)
                    }
                }
            }
        }
    }
    
    private static func loopFraction(time: Double, duration: Double) -> Double {
        guard duration > 0 else { return 0 }
        let x = time.truncatingRemainder(dividingBy: duration) / duration
        return max(0, min(1, x))
    }
    
    private static func smoothstep(_ t: Double) -> Double {
        let x = max(0, min(1, t))
        return x * x * (3 - 2 * x)
    }
    
    private static func lerp(_ a: Double, _ b: Double, _ t: Double) -> Double {
        a + (b - a) * t
    }
    
    private static func buildWordTargets(word: String) -> [CGPoint] {
        let text = word.trimmingCharacters(in: .whitespacesAndNewlines)
        if text.isEmpty { return [CGPoint(x: 0, y: 0)] }
        
        let width = 1000
        let height = 260
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: width, height: height))
        let image = renderer.image { ctx in
            UIColor.clear.setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: width, height: height))
            
            let font = UIFont.systemFont(ofSize: 170, weight: .heavy)
            let size = (text as NSString).size(withAttributes: [.font: font])
            let rect = CGRect(
                x: 0,
                y: (CGFloat(height) - size.height) / 2,
                width: CGFloat(width),
                height: size.height
            )
            
            let style = NSMutableParagraphStyle()
            style.alignment = .center
            (text as NSString).draw(
                in: rect,
                withAttributes: [
                    .font: font,
                    .foregroundColor: UIColor.white,
                    .paragraphStyle: style
                ]
            )
        }
        
        guard let cg = image.cgImage, let data = cg.dataProvider?.data else {
            return [CGPoint(x: 0, y: 0)]
        }
        let ptr = CFDataGetBytePtr(data)
        let bytesPerRow = cg.bytesPerRow
        
        var points: [CGPoint] = []
        points.reserveCapacity(1600)
        let step = 8
        for y in stride(from: 0, to: height, by: step) {
            for x in stride(from: 0, to: width, by: step) {
                let i = y * bytesPerRow + x * 4
                let a = ptr?[i + 3] ?? 0
                if a > 18 {
                    points.append(
                        CGPoint(
                            x: (CGFloat(x) - CGFloat(width) / 2) / CGFloat(width),
                            y: (CGFloat(y) - CGFloat(height) / 2) / CGFloat(height)
                        )
                    )
                }
            }
        }
        return points.isEmpty ? [CGPoint(x: 0, y: 0)] : points
    }
}

private struct SeededGenerator: RandomNumberGenerator {
    private var state: UInt64
    init(seed: UInt64) { self.state = seed }
    mutating func next() -> UInt64 {
        state &+= 0x9E3779B97F4A7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58476D1CE4E5B9
        z = (z ^ (z >> 27)) &* 0x94D049BB133111EB
        return z ^ (z >> 31)
    }
}
