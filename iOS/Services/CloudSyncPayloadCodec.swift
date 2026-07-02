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
        guard let inflated = process(data: raw, operation: COMPRESSION_STREAM_DECODE) else { throw .inflateFailed }
        return inflated
    }
    
    private static func process(data: Data, operation: compression_stream_operation) -> Data? {
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
        guard compression_stream_init(&stream, operation, COMPRESSION_ZLIB) != COMPRESSION_STATUS_ERROR else { return nil }
        defer { compression_stream_destroy(&stream) }
        let flags = operation == COMPRESSION_STREAM_ENCODE ? Int32(COMPRESSION_STREAM_FINALIZE.rawValue) : 0
        return run(stream: &stream, data: data, bufferSize: bufferSize, flags: flags)
    }
    
    private static func run(
        stream: inout compression_stream,
        data: Data,
        bufferSize: Int,
        flags: Int32
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
                let status = compression_stream_process(&stream, flags)
                let written = bufferSize - stream.dst_size
                if written > 0 { dst.append(dstBuffer, count: written) }
                if status == COMPRESSION_STATUS_END { return dst }
                if status == COMPRESSION_STATUS_ERROR { return nil }
                if status == COMPRESSION_STATUS_OK && written == 0 && stream.src_size == 0 { return nil }
            }
        }
    }
}
