#pragma once

#include "../core/Rational.h"
#include <optional>
#include <string>
#include <variant>

namespace rhythm
{

// Sealed-equivalent: one of Beat / BracketOpen / BracketClose / Repeat / Modulation / SetBpm.
// Modelled with a tagged variant. The fields directly mirror the Kotlin sealed class.
class TrackItem
{
public:
    enum class Kind { Beat, BracketOpen, BracketClose, Repeat, Modulation, SetBpm };

    struct Beat
    {
        int                        displayNum;
        int                        displayDenom;
        bool                       active  { true };
        std::optional<std::string> soundId {};
        float                      volume  { 1.0f };

        Rational duration() const { return Rational ((int64_t) displayNum, (int64_t) displayDenom); }
        std::string label() const
        {
            if (displayDenom == 1) return std::to_string (displayNum);
            return std::to_string (displayNum) + "/" + std::to_string (displayDenom);
        }
        bool isRest() const { return ! active; }
    };

    struct BracketOpen  {};
    struct BracketClose {};

    struct Repeat
    {
        static constexpr int INFINITE_COUNT = -1;
        int count;
        bool isInfinite() const { return count == INFINITE_COUNT; }
        std::string toString() const { return isInfinite() ? "×∞" : "×" + std::to_string (count); }
    };

    struct Modulation
    {
        int p;
        int q;
        Rational    ratio()    const { return Rational ((int64_t) p, (int64_t) q); }
        std::string toString() const { return "×" + std::to_string (p) + "/" + std::to_string (q); }
    };

    struct SetBpm
    {
        double bpm;
        std::string toString() const { return "=" + std::to_string ((int) bpm); }
    };

    using Storage = std::variant<Beat, BracketOpen, BracketClose, Repeat, Modulation, SetBpm>;

    TrackItem (Beat         v) : storage_ (std::move (v)) {}
    TrackItem (BracketOpen  v) : storage_ (v) {}
    TrackItem (BracketClose v) : storage_ (v) {}
    TrackItem (Repeat       v) : storage_ (v) {}
    TrackItem (Modulation   v) : storage_ (v) {}
    TrackItem (SetBpm       v) : storage_ (v) {}

    Kind kind() const { return static_cast<Kind> (storage_.index()); }

    bool isBeat()         const { return std::holds_alternative<Beat>         (storage_); }
    bool isBracketOpen()  const { return std::holds_alternative<BracketOpen>  (storage_); }
    bool isBracketClose() const { return std::holds_alternative<BracketClose> (storage_); }
    bool isRepeat()       const { return std::holds_alternative<Repeat>       (storage_); }
    bool isModulation()   const { return std::holds_alternative<Modulation>   (storage_); }
    bool isSetBpm()       const { return std::holds_alternative<SetBpm>       (storage_); }
    bool isTempoChange()  const { return isModulation() || isSetBpm(); }

    template <typename T>       T* getIf()       { return std::get_if<T> (&storage_); }
    template <typename T> const T* getIf() const { return std::get_if<T> (&storage_); }

    template <typename T>       T& get()       { return std::get<T> (storage_); }
    template <typename T> const T& get() const { return std::get<T> (storage_); }

    const Storage& storage() const { return storage_; }
          Storage& storage()       { return storage_; }

private:
    Storage storage_;
};

} // namespace rhythm
