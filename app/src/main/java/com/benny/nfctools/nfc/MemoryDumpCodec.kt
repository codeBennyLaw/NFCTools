package com.benny.nfctools.nfc

import com.benny.nfctools.data.MemoryDump
import com.benny.nfctools.data.TagSnapshot

object MemoryDumpCodec {
    fun export(dump: MemoryDump): ByteArray = buildString {
        appendLine("# NFC Tools ${dump.format}; ${dump.blockSize} bytes per block")
        dump.blocks.forEachIndexed { index, block ->
            if (dump.blockSize == 16 && (index < 128 && index % 4 == 0 || index >= 128 && (index - 128) % 16 == 0)) {
                appendLine("+Sector: ${if (index < 128) index / 4 else 32 + (index - 128) / 16}")
            }
            appendLine(block?.replace(" ", "") ?: "?".repeat(dump.blockSize * 2))
        }
        dump.notes.forEach { appendLine("# $it") }
    }.toByteArray()

    fun parse(bytes: ByteArray, name: String): MemoryDump {
        require(bytes.isNotEmpty() && bytes.size <= NdefCodec.MAX_FILE_BYTES) { "Dump 文件大小无效" }
        if (name.endsWith(".bin", true)) {
            require(bytes.size in listOf(320, 1024, 2048, 4096)) { "二进制 Dump 支持 320 / 1024 / 2048 / 4096 字节的 MIFARE Classic 镜像" }
            return MemoryDump("MIFARE Classic", 16, bytes.toList().chunked(16).map { block -> block.joinToString("") { "%02X".format(it.toInt() and 255) } }, listOf("从 $name 导入，仅供查看和比较"))
        }
        val lines = bytes.toString(Charsets.UTF_8).lineSequence().map(String::trim).filter { it.isNotEmpty() && !it.startsWith('#') && !it.startsWith('+') }.toList()
        require(lines.isNotEmpty() && lines.size <= 256) { "没有有效数据块，或超过 256 块" }
        val width = lines.first().replace(" ", "").length
        require(width == 8 || width == 32) { "每行需为 4 或 16 字节的 HEX 数据" }
        val blocks = lines.map { line ->
            val hex = line.replace(" ", "").uppercase()
            require(hex.length == width && hex.all { it in "0123456789ABCDEF?-" }) { "Dump 行长度或字符无效" }
            if (hex.any { it == '?' || it == '-' }) null else hex
        }
        return MemoryDump(if (width == 32) "MIFARE Classic" else "Type 2 pages", width / 2, blocks, listOf("从 $name 导入，仅供查看和比较"))
    }
}

data class ComparisonLine(val label: String, val first: String, val second: String) { val equal get() = first == second }
object CardComparison {
    fun compare(a: TagSnapshot, b: TagSnapshot): List<ComparisonLine> {
        val first = a.messageBase64?.let(NdefCodec::fromBase64) ?: byteArrayOf()
        val second = b.messageBase64?.let(NdefCodec::fromBase64) ?: byteArrayOf()
        return listOf(
            ComparisonLine("UID", a.uid, b.uid), ComparisonLine("卡片类型", a.type, b.type),
            ComparisonLine("技术协议", a.technologies.sorted().joinToString(), b.technologies.sorted().joinToString()),
            ComparisonLine("NDEF 容量", a.capacity?.toString() ?: "未知", b.capacity?.toString() ?: "未知"),
            ComparisonLine("写入状态", a.writable?.toString() ?: "未知", b.writable?.toString() ?: "未知"),
            ComparisonLine("NDEF 字节数", first.size.toString(), second.size.toString()),
            ComparisonLine("NDEF 数据", a.messageBase64 ?: "无", b.messageBase64 ?: "无"),
            ComparisonLine("内存块", a.memory?.blocks?.joinToString("\n") ?: "未读取", b.memory?.blocks?.joinToString("\n") ?: "未读取"),
        )
    }
    fun differentOffsets(a: ByteArray, b: ByteArray): List<Int> = (0 until maxOf(a.size, b.size)).filter { a.getOrNull(it) != b.getOrNull(it) }
}
