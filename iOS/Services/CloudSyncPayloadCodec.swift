import Compression
import Foundation

public enum CloudSyncPayloadCodecError: Error, Sendable {
    case wrapperJSONInvalid
    case missingCompressedField(field: String)
    case base64DecodeFailed
    case inflateFailed
}

enum CloudSyncPayloadCodec {
    static func decompressIfNeeded(_ data: Data) throws(CloudSyncPayloadCodecError) -> Data {
        guard let objAny = try? JSONSerialization.jsonObject(with: data) else { return data }
        guard let obj = objAny as? [String: Any] else { return data }
        guard let z = obj["z"] as? [String: Any] else { return data }
        guard let ct = z["ct"] as? String else { throw .missingCompressedField(field: "ct") }
        guard let raw = Data(base64Encoded: ct) else { throw .base64DecodeFailed }
        guard let inflated = ZlibDataDecoder.decompress(raw) else { throw .inflateFailed }
        return inflated
    }
}

enum ZlibDataDecoder {
    static func decompress(_ data: Data) -> Data? {
        guard let payload = payload(from: data) else { return nil }
        guard let output = inflate(payload.deflate) else { return nil }
        guard payload.checksum.map({ $0 == adler32(output) }) ?? true else { return nil }
        return output
    }

    private static func inflate(_ data: Data) -> Data? {
        let bufferSize = 64 * 1024
        let scratchDst = UnsafeMutablePointer<UInt8>.allocate(capacity: 1)
        let scratchSrc = UnsafeMutablePointer<UInt8>.allocate(capacity: 1)
        defer {
            scratchDst.deallocate()
            scratchSrc.deallocate()
        }
        var stream = compression_stream(
            dst_ptr: scratchDst,
            dst_size: 0,
            src_ptr: UnsafePointer(scratchSrc),
            src_size: 0,
            state: nil
        )
        guard compression_stream_init(&stream, COMPRESSION_STREAM_DECODE, COMPRESSION_ZLIB) != COMPRESSION_STATUS_ERROR else { return nil }
        defer { compression_stream_destroy(&stream) }
        return run(stream: &stream, data: data, bufferSize: bufferSize)
    }

    private static let maxOutputBytes = 10 * 1024 * 1024

    private static func run(
        stream: inout compression_stream,
        data: Data,
        bufferSize: Int
    ) -> Data? {
        data.withUnsafeBytes { (srcPtr: UnsafeRawBufferPointer) -> Data? in
            guard let srcBase = srcPtr.bindMemory(to: UInt8.self).baseAddress else { return nil }
            var dst = Data()
            stream.src_ptr = srcBase
            stream.src_size = data.count
            let dstBuffer = UnsafeMutablePointer<UInt8>.allocate(capacity: bufferSize)
            defer { dstBuffer.deallocate() }
            while true {
                stream.dst_ptr = dstBuffer
                stream.dst_size = bufferSize
                let status = compression_stream_process(&stream, 0)
                let written = bufferSize - stream.dst_size
                if written > 0 { dst.append(dstBuffer, count: written) }
                if dst.count > maxOutputBytes { return nil }
                if status == COMPRESSION_STATUS_END { return dst }
                if status == COMPRESSION_STATUS_ERROR { return nil }
                if status == COMPRESSION_STATUS_OK && written == 0 && stream.src_size == 0 { return nil }
            }
        }
    }

    private static func payload(from data: Data) -> (deflate: Data, checksum: UInt32?)? {
        guard hasZlibHeader(data) else { return (data, nil) }
        guard data.count >= 6 else { return nil }
        let header = [UInt8](data.prefix(2))
        guard header[1] & 0x20 == 0 else { return nil }
        let checksum = data.suffix(4).reduce(UInt32(0)) { ($0 << 8) | UInt32($1) }
        return (Data(data.dropFirst(2).dropLast(4)), checksum)
    }

    private static func hasZlibHeader(_ data: Data) -> Bool {
        guard data.count >= 2 else { return false }
        let header = [UInt8](data.prefix(2))
        let methodIsDeflate = header[0] & 0x0F == 8 && header[0] >> 4 <= 7
        let checksumIsValid = (Int(header[0]) << 8 | Int(header[1])) % 31 == 0
        return methodIsDeflate && checksumIsValid
    }

    private static func adler32(_ data: Data) -> UInt32 {
        let modulus: UInt64 = 65_521
        var lower: UInt64 = 1
        var upper: UInt64 = 0
        for byte in data {
            lower = (lower + UInt64(byte)) % modulus
            upper = (upper + lower) % modulus
        }
        return UInt32((upper << 16) | lower)
    }
}
