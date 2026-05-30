#include "Interpreter.h"

#include <memory>
#include <variant>
#include <vector>

namespace rhythm
{

namespace
{

struct PBeat
{
    TrackItem::Beat item;
    int             idx;
};

struct PTempo
{
    const TrackItem* item; // points into draft.items
    int              idx;
};

struct PGroup;

using PNode = std::variant<PBeat, PTempo, std::shared_ptr<PGroup>>;

struct PGroup
{
    std::vector<PNode>     children;
    int                    repeatCount { 1 };
    std::vector<TrackItem> modifiers; // TrackItem copies (Modulation or SetBpm)
    int                    openIdx { 0 };
};

struct Frame
{
    std::vector<PNode> children;
    int                openIdx;
};

struct ComputeState
{
    double  nanosPerPulse { 0.0 }; // 'npp' in the Kotlin source
    int64_t cursor        { 0 };
    int     fired         { 0 };
};

static double bpmToNpp (double bpm) { return 60'000'000'000.0 / bpm; }

static void addNode (PNode n, std::vector<PNode>& roots, std::vector<Frame>& stack)
{
    if (stack.empty())
        roots.push_back (std::move (n));
    else
        stack.back().children.push_back (std::move (n));
}

static void applyTempo (const TrackItem& item, ComputeState& s,
                        std::vector<std::pair<int64_t, double>>& tempoMap)
{
    double newNpp = s.nanosPerPulse;
    if (const auto* m = item.getIf<TrackItem::Modulation>(); m != nullptr)
        newNpp = s.nanosPerPulse * (double) m->q / (double) m->p;
    else if (const auto* b = item.getIf<TrackItem::SetBpm>(); b != nullptr)
        newNpp = bpmToNpp (b->bpm);
    else
        return;
    tempoMap.emplace_back (s.cursor, 60'000'000'000.0 / newNpp);
    s.nanosPerPulse = newNpp;
}

static void computeNode (const PNode& node,
                         ComputeState& s,
                         std::vector<PrecomputedEvent>& out,
                         std::vector<std::pair<int64_t, double>>& tempoMap,
                         const std::string& defaultSound);

static void computeNode (const PNode& node,
                         ComputeState& s,
                         std::vector<PrecomputedEvent>& out,
                         std::vector<std::pair<int64_t, double>>& tempoMap,
                         const std::string& defaultSound)
{
    if (const auto* beat = std::get_if<PBeat> (&node))
    {
        const auto dur = (int64_t) (s.nanosPerPulse * beat->item.displayNum / beat->item.displayDenom);
        PrecomputedEvent e;
        e.offsetNanos    = s.cursor;
        if (beat->item.active)
            e.soundId    = beat->item.soundId.has_value() ? *beat->item.soundId : defaultSound;
        else
            e.soundId    = std::nullopt;
        e.trackItemIndex = beat->idx;
        e.firedCount     = s.fired;
        e.volume         = beat->item.volume;
        out.push_back (std::move (e));
        s.cursor += dur;
        ++s.fired;
        return;
    }
    if (const auto* tempo = std::get_if<PTempo> (&node))
    {
        applyTempo (*tempo->item, s, tempoMap);
        return;
    }
    if (const auto* groupPtr = std::get_if<std::shared_ptr<PGroup>> (&node))
    {
        const PGroup& g = **groupPtr;
        const int passes = (g.repeatCount == TrackItem::Repeat::INFINITE_COUNT)
                           ? 1 : std::max (1, g.repeatCount);
        for (int p = 0; p < passes; ++p)
            for (const auto& c : g.children)
                computeNode (c, s, out, tempoMap, defaultSound);
        // modifiers apply once after all passes, and persist outward
        for (const auto& mod : g.modifiers)
            applyTempo (mod, s, tempoMap);
    }
}

} // namespace

InterpretResult interpretTrackDraft (const TrackDraft& draft,
                                     double baseBpm,
                                     const std::string& defaultSound)
{
    // ----- parse: flat items → PNode tree -----
    std::vector<PNode>  rootNodes;
    std::vector<Frame>  stack;

    int i = 0;
    const int n = (int) draft.items.size();
    while (i < n)
    {
        const auto& item = draft.items[(size_t) i];
        if (const auto* b = item.getIf<TrackItem::Beat>())
        {
            addNode (PBeat { *b, i }, rootNodes, stack);
        }
        else if (item.isModulation() || item.isSetBpm())
        {
            addNode (PTempo { &draft.items[(size_t) i], i }, rootNodes, stack);
        }
        else if (item.isBracketOpen())
        {
            stack.push_back (Frame { {}, i });
        }
        else if (item.isBracketClose())
        {
            if (stack.empty()) { ++i; continue; }
            Frame frame = std::move (stack.back());
            stack.pop_back();

            int repeatCount = 1;
            std::vector<TrackItem> modifiers;

            int j = i + 1;
            if (j < n)
            {
                if (const auto* r = draft.items[(size_t) j].getIf<TrackItem::Repeat>())
                {
                    repeatCount = r->count;
                    ++j;
                }
            }
            while (j < n)
            {
                const auto& nxt = draft.items[(size_t) j];
                if (! (nxt.isModulation() || nxt.isSetBpm())) break;
                modifiers.push_back (nxt);
                ++j;
            }
            i = j - 1;
            auto g = std::make_shared<PGroup>();
            g->children    = std::move (frame.children);
            g->repeatCount = repeatCount;
            g->modifiers   = std::move (modifiers);
            g->openIdx     = frame.openIdx;
            addNode (g, rootNodes, stack);
        }
        // bare Repeat / Modulation / SetBpm consumed inline above; nothing to do here
        ++i;
    }
    // unclosed brackets become unrepeated groups
    while (! stack.empty())
    {
        Frame frame = std::move (stack.back());
        stack.pop_back();
        auto g = std::make_shared<PGroup>();
        g->children    = std::move (frame.children);
        g->repeatCount = 1;
        g->openIdx     = frame.openIdx;
        addNode (g, rootNodes, stack);
    }

    // ----- compute: PNode tree → events -----
    auto hasInfLoop = [&]() -> bool
    {
        if (rootNodes.empty()) return false;
        if (const auto* gp = std::get_if<std::shared_ptr<PGroup>> (&rootNodes.back()))
            return (*gp)->repeatCount == TrackItem::Repeat::INFINITE_COUNT;
        return false;
    }();

    std::vector<PrecomputedEvent>            events;
    std::vector<std::pair<int64_t, double>>  tempoMap;
    ComputeState s { bpmToNpp (baseBpm), 0, 0 };

    const int prefixEnd = hasInfLoop ? (int) rootNodes.size() - 1
                                     : (int) rootNodes.size();
    for (int k = 0; k < prefixEnd; ++k)
        computeNode (rootNodes[(size_t) k], s, events, tempoMap, defaultSound);

    const int64_t loopNanos = s.cursor;

    std::vector<PrecomputedEvent> infPassEvents;
    int64_t infPassNanos = 0;
    if (hasInfLoop)
    {
        const auto& infGroupPtr = std::get<std::shared_ptr<PGroup>> (rootNodes.back());
        const PGroup& infGroup = *infGroupPtr;
        ComputeState is2 { s.nanosPerPulse, 0, 0 };
        for (const auto& c : infGroup.children)
            computeNode (c, is2, infPassEvents, tempoMap, defaultSound);
        for (const auto& mod : infGroup.modifiers)
            applyTempo (mod, is2, tempoMap);
        infPassNanos = is2.cursor;
    }

    InterpretResult r;
    r.trackId            = draft.id;
    r.events             = std::move (events);
    r.loopNanos          = loopNanos;
    r.infinitePassEvents = std::move (infPassEvents);
    r.infinitePassNanos  = infPassNanos;
    r.tempoMapNanos      = std::move (tempoMap);
    return r;
}

} // namespace rhythm
