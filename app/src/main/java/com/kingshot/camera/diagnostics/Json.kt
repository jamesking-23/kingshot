package com.kingshot.camera.diagnostics

/** Minimal JSON writer (no dependency). Supports Map, Iterable, Pair, String, Number, Boolean, null. */
object Json {
    fun encode(value: Any?): String = StringBuilder().also { write(it, value, 0) }.toString()

    fun quote(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c.code < 0x20 -> sb.append(String.format("\\u%04x", c.code))
                else -> sb.append(c)
            }
        }
        return sb.append('"').toString()
    }

    private fun indent(sb: StringBuilder, depth: Int) {
        repeat(depth) { sb.append("  ") }
    }

    private fun write(sb: StringBuilder, v: Any?, depth: Int) {
        when (v) {
            null -> sb.append("null")
            is String -> sb.append(quote(v))
            is Boolean -> sb.append(v.toString())
            is Float -> sb.append(if (v.isNaN() || v.isInfinite()) "null" else v.toString())
            is Double -> sb.append(if (v.isNaN() || v.isInfinite()) "null" else v.toString())
            is Number -> sb.append(v.toString())
            is Pair<*, *> -> write(sb, listOf(v.first, v.second), depth)
            is Map<*, *> -> {
                if (v.isEmpty()) {
                    sb.append("{}")
                } else {
                    sb.append("{\n")
                    var first = true
                    for ((k, value) in v) {
                        if (!first) sb.append(",\n")
                        first = false
                        indent(sb, depth + 1)
                        sb.append(quote(k.toString())).append(": ")
                        write(sb, value, depth + 1)
                    }
                    sb.append('\n')
                    indent(sb, depth)
                    sb.append('}')
                }
            }
            is Iterable<*> -> {
                val items = v.toList()
                if (items.isEmpty()) {
                    sb.append("[]")
                } else if (items.all { it == null || it is Number || it is Boolean || (it is String && it.length < 40) }) {
                    sb.append('[')
                    items.forEachIndexed { i, item ->
                        if (i > 0) sb.append(", ")
                        write(sb, item, depth)
                    }
                    sb.append(']')
                } else {
                    sb.append("[\n")
                    items.forEachIndexed { i, item ->
                        if (i > 0) sb.append(",\n")
                        indent(sb, depth + 1)
                        write(sb, item, depth + 1)
                    }
                    sb.append('\n')
                    indent(sb, depth)
                    sb.append(']')
                }
            }
            else -> sb.append(quote(v.toString()))
        }
    }

    /** Readable "path: value" rendering of the same structure, for the .txt twin of a JSON file. */
    fun toText(value: Any?, title: String = ""): String {
        val sb = StringBuilder()
        if (title.isNotEmpty()) sb.append(title).append('\n')
        text(sb, value, 0, "")
        return sb.toString()
    }

    private fun text(sb: StringBuilder, v: Any?, depth: Int, label: String) {
        val pad = "  ".repeat(depth)
        when (v) {
            is Map<*, *> -> {
                if (label.isNotEmpty()) sb.append(pad).append(label).append(":\n")
                val d = if (label.isNotEmpty()) depth + 1 else depth
                for ((k, value) in v) text(sb, value, d, k.toString())
            }
            is Iterable<*> -> {
                val items = v.toList()
                if (items.all { it !is Map<*, *> && it !is Iterable<*> }) {
                    sb.append(pad).append(label).append(": ").append(items.joinToString(", ") { it?.toString() ?: "null" }).append('\n')
                } else {
                    sb.append(pad).append(label).append(":\n")
                    items.forEachIndexed { i, item -> text(sb, item, depth + 1, "[$i]") }
                }
            }
            is Pair<*, *> -> sb.append(pad).append(label).append(": ").append(v.first).append(" .. ").append(v.second).append('\n')
            else -> sb.append(pad).append(label).append(": ").append(v?.toString() ?: "null").append('\n')
        }
    }
}
