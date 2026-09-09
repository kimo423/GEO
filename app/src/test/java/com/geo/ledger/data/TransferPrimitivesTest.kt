package com.geo.ledger.data

import com.geo.ledger.data.attachments.AttachmentPolicy as P
import com.geo.ledger.data.transfer.StrictJson
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.OutputStream

class TransferPrimitivesTest {
    @Test fun strictUtf8RejectsDamagedConfigurationNames() {
        assertEquals("中文",StrictJson.decodeUtf8("中文".toByteArray()))
        assertThrows(java.nio.charset.CharacterCodingException::class.java) {
            StrictJson.decodeUtf8(byteArrayOf(0xc3.toByte(),0x28))
        }
    }
    @Test fun limitFailuresGiveDistinctMessages() {
        assertEquals(P.COUNT_ERROR,assertThrows(IllegalArgumentException::class.java) { P.validate(List(11){1L}) }.message)
        assertEquals(P.FILE_ERROR,assertThrows(IllegalArgumentException::class.java) { P.validate(listOf(P.MAX_FILE+1)) }.message)
        assertEquals(P.TOTAL_ERROR,assertThrows(IllegalArgumentException::class.java) { P.validate(List(4){P.MAX_FILE}) }.message)
        val sink=object:OutputStream(){override fun write(b:Int){}}
        assertEquals(P.TOTAL_ERROR,assertThrows(IllegalArgumentException::class.java) { P.copy(byteArrayOf(1,2).inputStream(),sink,1,P.TOTAL_ERROR) }.message)
    }
    @Test fun exactLimitsAndEmptyAllowed() {
        P.validate(emptyList()); P.validate(listOf(1)); P.validate(List(10) { 1 })
        P.validate(List(3) { P.MAX_FILE })
        listOf(List(11) { 1L }, listOf(P.MAX_FILE + 1), List(3) { P.MAX_FILE } + 1L).forEach {
            assertThrows(IllegalArgumentException::class.java) { P.validate(it) }
        }
    }
    @Test fun streamingActualSizeIgnoresMetadata() {
        val sink = object : OutputStream() { override fun write(b: Int) {} }
        assertEquals(P.MAX_FILE, P.copy(ByteArrayInputStream(ByteArray(P.MAX_FILE.toInt())), sink).size)
        assertThrows(IllegalArgumentException::class.java) {
            P.copy(ByteArrayInputStream(ByteArray(P.MAX_FILE.toInt() + 1)), sink)
        }
    }
    @Test fun hashKnownVector() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", P.copy(
            ByteArrayInputStream("abc".toByteArray()), object : OutputStream() { override fun write(b: Int) {} }
        ).sha256)
    }
    @Test fun jsonRoundTripAndStrictFailures() {
        val value = linkedMapOf("中文" to "\n\"📎", "cents" to Long.MAX_VALUE, "list" to listOf(null, true, -1L))
        assertEquals(value, StrictJson.parse(StrictJson.stringify(value)))
        listOf("{\"a\":1,\"a\":2}", "[1,]", "1.1", "01", "9223372036854775808", "{}x", "NaN").forEach {
            assertThrows(RuntimeException::class.java) { StrictJson.parse(it) }
        }
        assertEquals("文",P.truncateText("文📎",2))
        assertEquals("文📎",P.truncateText("文📎",3))
        assertThrows(RuntimeException::class.java) { StrictJson.parse("\"\\ud800\"") }
    }
}
