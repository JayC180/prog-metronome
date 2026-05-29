package com.jayc180.rhythmengine.core

import kotlin.math.abs
import kotlin.math.sign

/**
 * Immutable rational number. The only numeric type for durations inside the engine
 *
 * NO float pt until the audio boundary (TempoContext.toNanos)
 * All arithmetic auto-reduces. Denominator is always positive after reduction
 */
data class Rational(val num: Long, val den: Long) : Comparable<Rational> {

    init {
        require(den != 0L) { "Rational denominator cannot be zero" }
    }

    companion object {
        val ZERO = Rational(0, 1)
        val ONE  = Rational(1, 1)
        val TWO  = Rational(2, 1)

        fun of(num: Int, den: Int)   = Rational(num.toLong(), den.toLong())
        fun of(num: Long, den: Long) = Rational(num, den)
        fun of(whole: Int)           = Rational(whole.toLong(), 1L)

        private fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)
    }

    // canonical form
    val reduced: Rational by lazy {
        if (num == 0L) return@lazy ZERO
        val g    = gcd(abs(num), abs(den))
        val sign = den.sign.toLong()
        Rational(sign * num / g, sign * den / g)
    }

    // arithmetics

    operator fun plus(other: Rational)  = Rational(num * other.den + other.num * den, den * other.den).reduced
    operator fun minus(other: Rational) = Rational(num * other.den - other.num * den, den * other.den).reduced
    operator fun times(other: Rational) = Rational(num * other.num, den * other.den).reduced
    operator fun div(other: Rational): Rational {
        require(other.num != 0L) { "Division by zero" }
        return Rational(num * other.den, den * other.num).reduced
    }
    operator fun unaryMinus() = Rational(-num, den)

    // comparison

    override fun compareTo(other: Rational): Int = (num * other.den).compareTo(other.num * den)

    fun isZero()     = num == 0L
    fun isPositive() = (num > 0L) == (den > 0L) && !isZero()
    fun isNegative() = !isZero() && !isPositive()

    /**
     * -> ticks. Integer division!!
     *
     * TICKS_PER_PULSE = 55440, chosen as LCM of 1..12 plus 14,16,32,64
     * Every subdivision converts with zero rounding error
     */
    fun toTicks(): Long = (num * TICKS_PER_PULSE) / den

    // display
    // Raw inputted ratio string — the only display format. No note-name mapping
    override fun toString(): String {
        val r = reduced
        return if (r.den == 1L) "${r.num}" else "${r.num}/${r.den}"
    }

    fun toDouble(): Double = num.toDouble() / den.toDouble()
}

// global tick resolution
const val TICKS_PER_PULSE = 55440L

// infix convenience. 5 over 4 -> Rational(5, 4)
infix fun Int.over(den: Int)   = Rational.of(this, den)
infix fun Long.over(den: Long) = Rational.of(this, den)