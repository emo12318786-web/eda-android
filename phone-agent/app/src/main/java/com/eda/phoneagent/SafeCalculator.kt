package com.eda.phoneagent

/** Small arithmetic parser; evaluates no code and supports + - * / % and parentheses. */
internal object SafeCalculator {
    fun eval(source: String): Double {
        class Parser(private val s: String) {
            var i = 0
            fun parse(): Double {
                val value = expression()
                if (i != s.length) error("Unexpected token")
                if (!value.isFinite()) error("Non-finite result")
                return value
            }
            private fun expression(): Double {
                var v = term()
                while (i < s.length && (s[i] == '+' || s[i] == '-')) {
                    val op = s[i++]; val rhs = term()
                    v = if (op == '+') v + rhs else v - rhs
                }
                return v
            }
            private fun term(): Double {
                var v = factor()
                while (i < s.length && (s[i] == '*' || s[i] == '/' || s[i] == '%')) {
                    val op = s[i++]; val rhs = factor()
                    if ((op == '/' || op == '%') && rhs == 0.0) error("Division by zero")
                    v = when (op) { '*' -> v * rhs; '/' -> v / rhs; else -> v % rhs }
                }
                return v
            }
            private fun factor(): Double {
                if (i >= s.length) error("Missing value")
                if (s[i] == '+') { i++; return factor() }
                if (s[i] == '-') { i++; return -factor() }
                if (s[i] == '(') {
                    i++; val v = expression()
                    if (i >= s.length || s[i] != ')') error("Missing )")
                    i++; return v
                }
                val start = i
                var dots = 0
                while (i < s.length && (s[i].isDigit() || s[i] == '.')) {
                    if (s[i] == '.') dots++
                    if (dots > 1) error("Bad number")
                    i++
                }
                if (start == i) error("Expected number")
                return s.substring(start, i).toDouble()
            }
        }
        return Parser(source).parse()
    }
}
