package com.eda.phoneagent

/** Safe arithmetic parser. It never evaluates source code. */
internal object SafeCalculator {
    fun eval(source: String): Double {
        require(source.length <= 80) { "Expression too long" }
        class Parser(private val s: String) {
            var i = 0
            fun parse(): Double {
                val result = expression()
                require(i == s.length) { "Unexpected token" }
                require(result.isFinite()) { "Invalid result" }
                return result
            }
            private fun expression(): Double {
                var v = term()
                while (i < s.length && (s[i] == '+' || s[i] == '-')) {
                    val op = s[i++]; val r = term()
                    v = if (op == '+') v + r else v - r
                }
                return v
            }
            private fun term(): Double {
                var v = factor()
                while (i < s.length && (s[i] == '*' || s[i] == '/' || s[i] == '%')) {
                    val op = s[i++]; val r = factor()
                    require(!((op == '/' || op == '%') && r == 0.0)) { "Division by zero" }
                    v = when (op) { '*' -> v * r; '/' -> v / r; else -> v % r }
                }
                return v
            }
            private fun factor(): Double {
                require(i < s.length) { "Missing number" }
                if (s[i] == '+') { i++; return factor() }
                if (s[i] == '-') { i++; return -factor() }
                if (s[i] == '(') {
                    i++; val v = expression()
                    require(i < s.length && s[i] == ')') { "Missing )" }; i++
                    return v
                }
                val start = i; var dots = 0
                while (i < s.length && (s[i].isDigit() || s[i] == '.')) {
                    if (s[i] == '.') dots++
                    require(dots <= 1) { "Invalid number" }; i++
                }
                require(start != i) { "Expected number" }
                return s.substring(start, i).toDouble()
            }
        }
        require(source.isNotBlank() && source.matches(Regex("[0-9+\\-*/().%]+"))) { "Invalid characters" }
        return Parser(source).parse()
    }
}
