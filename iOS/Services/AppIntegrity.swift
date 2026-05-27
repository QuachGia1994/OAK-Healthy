import Foundation
import Darwin

public struct AppIntegrityVerdict: Sendable, Equatable {
    public let ok: Bool
    public let reason: String
    
    public init(ok: Bool, reason: String) {
        self.ok = ok
        self.reason = reason
    }
}

public enum AppIntegrity {
    public static func evaluate() -> AppIntegrityVerdict {
#if DEBUG
        return AppIntegrityVerdict(ok: true, reason: "debug")
#else
#if targetEnvironment(simulator)
        return AppIntegrityVerdict(ok: true, reason: "simulator")
#else
        if isDebuggerAttached() { return AppIntegrityVerdict(ok: false, reason: "debugger") }
        if hasInjectionEnvironment() { return AppIntegrityVerdict(ok: false, reason: "injection") }
        if isJailbroken() { return AppIntegrityVerdict(ok: false, reason: "jailbreak") }
        return AppIntegrityVerdict(ok: true, reason: "ok")
#endif
#endif
    }
    
    private static func hasInjectionEnvironment() -> Bool {
        let env = ProcessInfo.processInfo.environment
        let dyld = (env["DYLD_INSERT_LIBRARIES"] ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        if !dyld.isEmpty { return true }
        let frida = (env["FRIDA"] ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        if !frida.isEmpty { return true }
        return false
    }
    
    private static func isJailbroken() -> Bool {
        let paths = [
            "/Applications/Cydia.app",
            "/Library/MobileSubstrate/MobileSubstrate.dylib",
            "/bin/bash",
            "/usr/sbin/sshd",
            "/etc/apt",
            "/private/var/lib/apt/"
        ]
        if paths.contains(where: { FileManager.default.fileExists(atPath: $0) }) { return true }
        let testPath = "/private/" + UUID().uuidString
        do {
            try "x".write(toFile: testPath, atomically: true, encoding: .utf8)
            try? FileManager.default.removeItem(atPath: testPath)
            return true
        } catch {
            return false
        }
    }
    
    private static func isDebuggerAttached() -> Bool {
        var name = [CTL_KERN, KERN_PROC, KERN_PROC_PID, getpid()]
        var info = kinfo_proc()
        var size = MemoryLayout<kinfo_proc>.stride
        let result = sysctl(&name, 4, &info, &size, nil, 0)
        if result != 0 { return false }
        return (info.kp_proc.p_flag & P_TRACED) != 0
    }
}
