import Compression
import Foundation

enum CloudSyncPayloadCodecError: Error, Sendable {
    case invalidCompressedPayload
}

enum CloudSyncPayloadCodec {
    private static let thresholdBytes = 40_000
    
    static func compressIfUseful(_ jsonData: Data) -> Data {
        guard jsonData.count >= thresholdBytes else { return jsonData }
        guard let compressed = process(data: jsonData, operation: COMPRESSION_STREAM_ENCODE) else { return jsonData }
        let wrapper: [String: Any] = [
            "z": [
                "v": 1,
                "alg": "ZLIB",
                "ct": compressed.base64EncodedString()
            ]
        ]
        guard let wrapperData = try? JSONSerialization.data(withJSONObject: wrapper, options: []) else { return jsonData }
        if wrapperData.count >= jsonData.count - 1024 { return jsonData }
        return wrapperData
    }
    
    static func decompressIfNeeded(_ data: Data) throws(CloudSyncPayloadCodecError) -> Data {
        guard let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return data }
        guard let z = obj["z"] as? [String: Any] else { return data }
        guard let ct = z["ct"] as? String, let raw = Data(base64Encoded: ct) else { throw .invalidCompressedPayload }
        guard let inflated = process(data: raw, operation: COMPRESSION_STREAM_DECODE) else { throw .invalidCompressedPayload }
        return inflated
    }
    
    private static func process(data: Data, operation: compression_stream_operation) -> Data? {
        let bufferSize = 64 * 1024
        var stream = compression_stream()
        guard compression_stream_init(&stream, operation, COMPRESSION_ZLIB) != COMPRESSION_STATUS_ERROR else { return nil }
        defer { compression_stream_destroy(&stream) }
        return data.withUnsafeBytes { srcPtr in
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
                if status == COMPRESSION_STATUS_END { return dst }
                if status == COMPRESSION_STATUS_ERROR { return nil }
            }
        }
    }
}

