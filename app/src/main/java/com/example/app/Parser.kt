@file:Suppress("unused")

package com.example.app

import kotlin.math.max

typealias Parser<T> = (String, Int) -> Pair<T, Int>?

fun <T> Parser<T>.parse(source: String): T? {
    val result = invoke(source, 0)

    return when {
        result == null || result.second != source.length ->
            null
        else ->
            result.first
    }
}

fun <T> parserPure(x: T): Parser<T> =
    { _, i -> Pair(x, i) }

val parserError: Parser<Nothing> = { _, _ -> null }

fun <T, U> Parser<T>.map(f: (T) -> U): Parser<U> =
    { s, i -> invoke(s, i)?.let { (x, i) -> Pair(f(x), i) } }

fun <T, U> Parser<T>.flatMap(f: (T) -> Parser<U>): Parser<U> =
    { s, i -> invoke(s, i)?.let { (x, i) -> f(x)(s, i) } }

fun <T> parserOr(pa: Parser<T>, pb: Parser<T>): Parser<T> =
    { s, i -> pa(s, i) ?: pb(s, i) }

fun <T> Parser<T>.parserCatch(x: T): Parser<T> =
    { s, i -> invoke(s, i) ?: Pair(x, i) }

fun <T, U> parserAnd(pa: Parser<T>, pb: Parser<U>): Parser<Pair<T, U>> =
    { s, i ->
        val ra = pa(s, i)
        if (ra == null) {
            null
        } else {
            val rb = pb(s, i)
            if (rb == null) {
                null
            } else {
                Pair(Pair(ra.first, rb.first), max(ra.second, rb.second))
            }
        }
    }

fun parserChar(c: Char): Parser<Char> =
    { s, i -> if (s.getOrNull(i) == c) Pair(c, i + 1) else null }

fun parserString(string: String): Parser<String> =
    { s, i ->
        if (s.regionMatches(i, string, 0, string.length)) {
            Pair(string, i + string.length)
        } else {
            null
        }
    }

fun parserWhile(p: (Char) -> Boolean): Parser<String> =
    { s, i ->
        var k = i
        while (k < s.length && p(s[k])) k++
        Pair(s.slice(i until k), k)
    }

val parserDigits: Parser<String> =
    parserWhile { it.isDigit() }

val parserNonNegInt: Parser<Int> = parserDigits
    .flatMap { digits -> digits.toIntOrNull()?.let(::parserPure) ?: parserError }
