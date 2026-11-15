package com.tongzhouweiji.baichuan.core.tts

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class DeepSeekTtsTest {

    @Test
    fun hanCountsAsTwoBillingChars() {
        // 你(2) + 好(2) + a(1) + b(1) = 6
        assertEquals("你好ab", DeepSeekTts.clipForBilling("你好ab", 6))
        assertEquals("你好ab", DeepSeekTts.clipForBilling("你好abc", 6))
    }

    @Test
    fun longChineseTextStopsAtTenThousandBillingChars() {
        val text = "汉".repeat(6000)
        val clipped = DeepSeekTts.clipForBilling(text)
        assertEquals(5000, clipped.length)
    }

    @Test
    fun streamingWavHeaderIsRewritten() {
        val bytes = ByteArray(48)
        bytes[0] = 'R'.code.toByte()
        bytes[1] = 'I'.code.toByte()
        bytes[2] = 'F'.code.toByte()
        bytes[3] = 'F'.code.toByte()
        bytes[8] = 'W'.code.toByte()
        bytes[9] = 'A'.code.toByte()
        bytes[10] = 'V'.code.toByte()
        bytes[11] = 'E'.code.toByte()
        // fmt chunk
        bytes[12] = 'f'.code.toByte()
        bytes[13] = 'm'.code.toByte()
        bytes[14] = 't'.code.toByte()
        bytes[15] = ' '.code.toByte()
        writeLe32(bytes, 16, 16)
        // data chunk（长度占位为 0）
        bytes[36] = 'd'.code.toByte()
        bytes[37] = 'a'.code.toByte()
        bytes[38] = 't'.code.toByte()
        bytes[39] = 'a'.code.toByte()
        writeLe32(bytes, 40, 0)
        bytes[44] = 1
        bytes[45] = 2
        bytes[46] = 3
        bytes[47] = 4

        val fixed = DeepSeekTts.fixWavHeader(bytes)
        assertEquals(40, readLe32(fixed, 4))
        assertEquals(4, readLe32(fixed, 40))
    }

    @Test
    fun nonWavBytesAreLeftUntouched() {
        val raw = ByteArray(48) { 0x41 }
        assertArrayEquals(raw, DeepSeekTts.fixWavHeader(raw))
    }

    private fun readLe32(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    private fun writeLe32(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xFF).toByte()
        bytes[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        bytes[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        bytes[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }
}
