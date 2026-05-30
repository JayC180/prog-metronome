#pragma once

#include <cstdint>
#include <cstdlib>
#include <stdexcept>
#include <string>

namespace rhythm
{

// Global tick resolution. Chosen as LCM of 1..12 plus 14,16,32,64.
// Every subdivision used in the engine converts to ticks with zero rounding error.
inline constexpr int64_t TICKS_PER_PULSE = 55440;

// Immutable rational number — the only numeric type for durations inside the engine.
// No floating point until the audio boundary (TempoContext::toNanos).
// All arithmetic auto-reduces. Denominator is always positive after reduction.
class Rational
{
public:
    constexpr Rational() : num_ (0), den_ (1) {}
    constexpr Rational (int64_t n, int64_t d) : num_ (n), den_ (d)
    {
        if (d == 0)
            throw std::invalid_argument ("Rational denominator cannot be zero");
    }

    static constexpr Rational of (int n, int d)            { return Rational ((int64_t) n, (int64_t) d); }
    static constexpr Rational of (int64_t n, int64_t d)    { return Rational (n, d); }
    static constexpr Rational ofWhole (int whole)          { return Rational ((int64_t) whole, 1); }

    static const Rational ZERO;
    static const Rational ONE;
    static const Rational TWO;

    constexpr int64_t num() const noexcept { return num_; }
    constexpr int64_t den() const noexcept { return den_; }

    // canonical form
    Rational reduced() const
    {
        if (num_ == 0) return ZERO;
        const int64_t g    = gcd (std::llabs (num_), std::llabs (den_));
        const int64_t sign = den_ < 0 ? -1 : 1;
        return Rational (sign * num_ / g, sign * den_ / g);
    }

    // arithmetic — always returns reduced form
    Rational operator+ (const Rational& o) const { return Rational (num_ * o.den_ + o.num_ * den_, den_ * o.den_).reduced(); }
    Rational operator- (const Rational& o) const { return Rational (num_ * o.den_ - o.num_ * den_, den_ * o.den_).reduced(); }
    Rational operator* (const Rational& o) const { return Rational (num_ * o.num_, den_ * o.den_).reduced(); }
    Rational operator/ (const Rational& o) const
    {
        if (o.num_ == 0) throw std::invalid_argument ("Division by zero");
        return Rational (num_ * o.den_, den_ * o.num_).reduced();
    }
    Rational operator- () const { return Rational (-num_, den_); }

    Rational& operator+= (const Rational& o) { *this = *this + o; return *this; }
    Rational& operator-= (const Rational& o) { *this = *this - o; return *this; }
    Rational& operator*= (const Rational& o) { *this = *this * o; return *this; }
    Rational& operator/= (const Rational& o) { *this = *this / o; return *this; }

    bool operator== (const Rational& o) const { return num_ * o.den_ == o.num_ * den_; }
    bool operator!= (const Rational& o) const { return ! (*this == o); }
    bool operator<  (const Rational& o) const { return num_ * o.den_ <  o.num_ * den_; }
    bool operator<= (const Rational& o) const { return num_ * o.den_ <= o.num_ * den_; }
    bool operator>  (const Rational& o) const { return num_ * o.den_ >  o.num_ * den_; }
    bool operator>= (const Rational& o) const { return num_ * o.den_ >= o.num_ * den_; }

    bool isZero()     const { return num_ == 0; }
    bool isPositive() const { return (num_ > 0) == (den_ > 0) && ! isZero(); }
    bool isNegative() const { return ! isZero() && ! isPositive(); }

    // Integer division to ticks — zero rounding error for any subdivision in the supported set.
    int64_t toTicks() const { return (num_ * TICKS_PER_PULSE) / den_; }

    double toDouble() const { return (double) num_ / (double) den_; }

    std::string toString() const
    {
        const auto r = reduced();
        if (r.den_ == 1) return std::to_string (r.num_);
        return std::to_string (r.num_) + "/" + std::to_string (r.den_);
    }

private:
    int64_t num_;
    int64_t den_;

    static int64_t gcd (int64_t a, int64_t b) { return b == 0 ? a : gcd (b, a % b); }
};

inline const Rational Rational::ZERO { 0, 1 };
inline const Rational Rational::ONE  { 1, 1 };
inline const Rational Rational::TWO  { 2, 1 };

} // namespace rhythm
