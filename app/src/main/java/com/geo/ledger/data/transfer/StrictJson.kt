package com.geo.ledger.data.transfer

/** Small deterministic JSON codec. Rejects duplicate keys, fractional money and deep nesting. */
object StrictJson {
    fun decodeUtf8(bytes: ByteArray): String = Charsets.UTF_8.newDecoder()
        .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
        .decode(java.nio.ByteBuffer.wrap(bytes)).toString()
    fun parse(text: String): Any? = Parser(text).parse()
    fun stringify(value: Any?): String = buildString { encode(value) }

    private fun StringBuilder.encode(value: Any?) {
        when (value) {
            null -> append("null")
            is String -> {
                validateUnicode(value)
                append('"')
                value.forEach { c ->
                    when (c) {
                        '"' -> append("\\\"")
                        '\\' -> append("\\\\")
                        '\n' -> append("\\n")
                        '\r' -> append("\\r")
                        '\t' -> append("\\t")
                        else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
                    }
                }
                append('"')
            }
            is Boolean, is Long, is Int -> append(value)
            is Map<*, *> -> {
                append('{')
                value.entries.forEachIndexed { index, e ->
                    if (index > 0) append(',')
                    require(e.key is String)
                    encode(e.key); append(':'); encode(e.value)
                }
                append('}')
            }
            is List<*> -> {
                append('[')
                value.forEachIndexed { index, item -> if (index > 0) append(','); encode(item) }
                append(']')
            }
            else -> error("Unsupported JSON type")
        }
    }

    private class Parser(val text: String) {
        var i = 0
        var nodes = 0
        fun parse(): Any? {
            require(text.length <= 32 * 1024 * 1024) { "JSON 过大" }
            val result = value(0); whitespace(); require(i == text.length) { "JSON 尾部数据无效" }; return result
        }
        fun whitespace() { while (i < text.length && text[i] in " \r\n\t") i++ }
        fun consume(c: Char): Boolean { whitespace(); return if (i < text.length && text[i] == c) { i++; true } else false }
        fun value(depth: Int): Any? {
            require(depth <= 48 && ++nodes <= 500_000) { "JSON 结构过大或嵌套过深" }
            whitespace(); require(i < text.length) { "JSON 不完整" }
            return when (text[i]) {
                '{' -> {
                    i++; val map = linkedMapOf<String, Any?>()
                    if (!consume('}')) {
                        do {
                            whitespace(); val key = string()
                            require(!map.containsKey(key)) { "JSON 键重复：$key" }
                            require(consume(':')) { "JSON 缺少冒号" }
                            map[key] = value(depth + 1)
                        } while (consume(','))
                        require(consume('}')) { "JSON 对象未结束" }
                    }
                    map
                }
                '[' -> {
                    i++; val list = mutableListOf<Any?>()
                    if (!consume(']')) {
                        do { list += value(depth + 1) } while (consume(','))
                        require(consume(']')) { "JSON 数组未结束" }
                    }
                    list
                }
                '"' -> string()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> {
                    val start = i
                    if (text[i] == '-') i++
                    require(i < text.length && text[i] in '0'..'9') { "JSON 数字无效" }
                    if (text[i] == '0') i++ else while (i < text.length && text[i] in '0'..'9') i++
                    text.substring(start, i).toLongOrNull() ?: error("JSON 整数越界")
                }
            }
        }
        fun literal(s: String, result: Any?): Any? {
            require(text.startsWith(s, i)) { "JSON 字面量无效" }; i += s.length; return result
        }
        fun string(): String {
            require(i < text.length && text[i++] == '"') { "JSON 缺少字符串" }
            val b = StringBuilder()
            while (i < text.length) {
                val c = text[i++]
                if (c == '"') return b.toString().also(::validateUnicode)
                require(c >= ' ') { "JSON 包含控制字符" }
                if (c != '\\') { b.append(c); continue }
                require(i < text.length)
                b.append(when (val e = text[i++]) {
                    '"', '\\', '/' -> e
                    'b' -> '\b'; 'f' -> '\u000c'; 'n' -> '\n'; 'r' -> '\r'; 't' -> '\t'
                    'u' -> {
                        require(i + 4 <= text.length)
                        val hex = text.substring(i, i + 4); i += 4
                        require(hex.all { it in "0123456789abcdefABCDEF" })
                        hex.toInt(16).toChar()
                    }
                    else -> error("JSON 转义无效")
                })
            }
            error("JSON 字符串未结束")
        }
    }

    private fun validateUnicode(value: String) {
        var i=0
        while(i<value.length) {
            val c=value[i++]
            if(Character.isHighSurrogate(c)) require(i<value.length && Character.isLowSurrogate(value[i++])) { "字符串包含不完整 Unicode" }
            else require(!Character.isLowSurrogate(c)) { "字符串包含不完整 Unicode" }
        }
    }
}

@Suppress("UNCHECKED_CAST")
fun Any?.jsonObject(): Map<String, Any?> = this as? Map<String, Any?> ?: error("需要 JSON 对象")
fun Map<String, Any?>.string(key: String): String = this[key] as? String ?: error("无效字符串：$key")
fun Map<String, Any?>.long(key: String): Long = this[key] as? Long ?: error("无效整数：$key")
fun Map<String, Any?>.bool(key: String): Boolean = this[key] as? Boolean ?: error("无效布尔值：$key")
fun Map<String, Any?>.array(key: String): List<Any?> = this[key] as? List<Any?> ?: error("无效数组：$key")
fun Map<String, Any?>.nullableString(key: String): String? = if (this[key] == null) null else string(key)
fun Map<String, Any?>.nullableLong(key: String): Long? = if (this[key] == null) null else long(key)
