#pragma once

#include "Rational.h"
#include <memory>
#include <optional>
#include <string>
#include <variant>
#include <vector>

namespace rhythm
{

// The rhythm tree. Every rhythmic structure in the engine is a BeatNode.
//
// Three node types:
//   Leaf     — a single indivisible slot
//   Group    — N slots inside the group's duration (tuplet, polyrhythm, etc.)
//   Sequence — an ordered list of nodes played end-to-end
//
// Invariants:
//   1. Duration is always a Rational multiple of the pulse unit. No note names.
//   2. A Leaf is active (soundId set) or silent (soundId nullopt).
//   3. Groups do NOT enforce children-sum-equals-duration. Incomplete groups
//      emit a ValidationWarning, never an error.
//   4. The tree is immutable. Edits produce new trees. The scheduler can
//      read while the UI builds a new draft.
//
// NOTE: the current builder flow uses TrackItem/TrackDraft, not BeatNode.
// BeatNode is kept in the engine as the documented tree model and is used
// by tests and any future tree-based authoring layer.

class BeatNode;
using BeatNodePtr = std::shared_ptr<const BeatNode>;

enum class Severity { Warning, Error };

struct ValidationWarning
{
    const BeatNode* node;
    std::string     message;
    Severity        severity { Severity::Warning };
};

// One schedulable slot — what the scheduler works with at runtime.
// soundId nullopt = silent (still emitted for cursor tracking).
struct FlatEvent
{
    Rational                   offset;
    Rational                   duration;
    std::optional<std::string> soundId;

    bool    isRest()   const { return ! soundId.has_value(); }
    bool    isActive() const { return soundId.has_value(); }
    int64_t offsetTicks()   const { return offset.toTicks(); }
    int64_t durationTicks() const { return duration.toTicks(); }
};

class BeatNode
{
public:
    enum class Kind { Leaf, Group, Sequence };

    virtual ~BeatNode() = default;
    virtual Kind     kind()     const = 0;
    virtual Rational duration() const = 0;

    // Flatten tree into ordered FlatEvents by time offset.
    // Called once at group-load time; the scheduler only handles FlatEvents.
    std::vector<FlatEvent> flatten() const
    {
        std::vector<FlatEvent> out;
        flattenInto (Rational::ZERO, out);
        return out;
    }

    std::vector<ValidationWarning> validate() const
    {
        std::vector<ValidationWarning> out;
        validateInto (out);
        return out;
    }

    // Public so sibling nodes (Group/Sequence) can recurse through children.
    virtual void flattenInto (const Rational& parentOffset,
                              std::vector<FlatEvent>& out) const = 0;
    virtual void validateInto (std::vector<ValidationWarning>& out) const = 0;
};

// Leaf — single indivisible slot. nullopt soundId = rest.
class LeafNode final : public BeatNode
{
public:
    LeafNode (Rational dur, std::optional<std::string> sound = std::nullopt)
        : duration_ (dur), soundId_ (std::move (sound)) {}

    Kind     kind()     const override { return Kind::Leaf; }
    Rational duration() const override { return duration_; }

    const std::optional<std::string>& soundId() const { return soundId_; }
    bool isRest()   const { return ! soundId_.has_value(); }
    bool isActive() const { return soundId_.has_value(); }

    static BeatNodePtr rest (Rational dur)
    {
        return std::make_shared<LeafNode> (dur);
    }
    static BeatNodePtr active (Rational dur, std::string sound)
    {
        return std::make_shared<LeafNode> (dur, std::optional<std::string> (std::move (sound)));
    }

    void flattenInto (const Rational& parentOffset,
                      std::vector<FlatEvent>& out) const override
    {
        out.push_back (FlatEvent { parentOffset, duration_, soundId_ });
    }
    void validateInto (std::vector<ValidationWarning>&) const override {}

private:
    Rational                   duration_;
    std::optional<std::string> soundId_;
};

// Group — tuplet/polyrhythm bracket with intended divisions.
class GroupNode final : public BeatNode
{
public:
    GroupNode (Rational dur, int divisions,
               std::vector<BeatNodePtr> children,
               std::optional<std::string> label = std::nullopt)
        : duration_ (dur), divisions_ (divisions),
          children_ (std::move (children)), label_ (std::move (label)) {}

    Kind     kind()     const override { return Kind::Group; }
    Rational duration() const override { return duration_; }

    int                                       divisions()   const { return divisions_; }
    const std::vector<BeatNodePtr>&           children()    const { return children_; }
    const std::optional<std::string>&         label()       const { return label_; }
    Rational slotDuration() const { return duration_ / Rational ((int64_t) divisions_, 1); }

    Rational childrenDuration() const
    {
        Rational sum = Rational::ZERO;
        for (auto& c : children_) sum += c->duration();
        return sum;
    }
    bool     isComplete() const { return childrenDuration() == duration_; }
    Rational gap()        const { return duration_ - childrenDuration(); }

    std::string displayLabel() const
    {
        return label_.has_value() ? *label_ : std::to_string (divisions_);
    }

    void flattenInto (const Rational& parentOffset,
                      std::vector<FlatEvent>& out) const override
    {
        Rational cursor = parentOffset;
        for (auto& c : children_) { c->flattenInto (cursor, out); cursor += c->duration(); }
    }
    void validateInto (std::vector<ValidationWarning>& out) const override
    {
        if (! isComplete())
        {
            const Rational g = gap();
            std::string msg = "Group(div=" + std::to_string (divisions_) + ") "
                            + (g.isPositive() ? "underfills" : "overfills")
                            + " by " + g.reduced().toString();
            out.push_back ({ this, std::move (msg),
                             g.isNegative() ? Severity::Error : Severity::Warning });
        }
        for (auto& c : children_) c->validateInto (out);
    }

private:
    Rational                   duration_;
    int                        divisions_;
    std::vector<BeatNodePtr>   children_;
    std::optional<std::string> label_;
};

// Sequence — ordered list played end-to-end. Total = sum of children.
class SequenceNode final : public BeatNode
{
public:
    explicit SequenceNode (std::vector<BeatNodePtr> nodes)
        : nodes_ (std::move (nodes)) {}

    Kind     kind() const override { return Kind::Sequence; }
    Rational duration() const override
    {
        Rational sum = Rational::ZERO;
        for (auto& n : nodes_) sum += n->duration();
        return sum;
    }

    const std::vector<BeatNodePtr>& nodes() const { return nodes_; }
    bool isEmpty() const { return nodes_.empty(); }

    void flattenInto (const Rational& parentOffset,
                      std::vector<FlatEvent>& out) const override
    {
        Rational cursor = parentOffset;
        for (auto& n : nodes_) { n->flattenInto (cursor, out); cursor += n->duration(); }
    }
    void validateInto (std::vector<ValidationWarning>& out) const override
    {
        if (isEmpty()) out.push_back ({ this, "Empty sequence", Severity::Warning });
        for (auto& n : nodes_) n->validateInto (out);
    }

private:
    std::vector<BeatNodePtr> nodes_;
};

} // namespace rhythm
