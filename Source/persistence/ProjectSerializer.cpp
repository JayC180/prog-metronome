#include "ProjectSerializer.h"
#include "../builder/TrackBuilder.h"

#include <juce_core/juce_core.h>

#include <chrono>

namespace rhythm
{

namespace
{

juce::var itemToVar (const TrackItem& item)
{
    auto* obj = new juce::DynamicObject();
    if (const auto* b = item.getIf<TrackItem::Beat>())
    {
        obj->setProperty ("type", "beat");
        obj->setProperty ("num",  b->displayNum);
        obj->setProperty ("den",  b->displayDenom);
        if (! b->active)                    obj->setProperty ("active",  false);
        if (b->soundId.has_value())         obj->setProperty ("soundId", juce::String (*b->soundId));
        if (b->volume != 1.0f)              obj->setProperty ("volume",  (double) b->volume);
    }
    else if (item.isBracketOpen())          obj->setProperty ("type", "open");
    else if (item.isBracketClose())         obj->setProperty ("type", "close");
    else if (const auto* r = item.getIf<TrackItem::Repeat>())
    {
        obj->setProperty ("type",  "repeat");
        obj->setProperty ("count", r->count);
    }
    else if (const auto* m = item.getIf<TrackItem::Modulation>())
    {
        obj->setProperty ("type", "mod");
        obj->setProperty ("p", m->p);
        obj->setProperty ("q", m->q);
    }
    else if (const auto* sb = item.getIf<TrackItem::SetBpm>())
    {
        obj->setProperty ("type",   "setbpm");
        obj->setProperty ("bpmVal", sb->bpm);
    }
    return juce::var (obj);
}

std::optional<TrackItem> varToItem (const juce::var& v)
{
    if (! v.isObject()) return std::nullopt;
    auto* obj = v.getDynamicObject();
    if (obj == nullptr) return std::nullopt;
    const juce::String type = obj->getProperty ("type").toString();

    if (type == "beat")
    {
        if (! obj->hasProperty ("num") || ! obj->hasProperty ("den")) return std::nullopt;
        TrackItem::Beat b;
        b.displayNum   = (int) obj->getProperty ("num");
        b.displayDenom = (int) obj->getProperty ("den");
        if (obj->hasProperty ("active")) b.active = (bool) obj->getProperty ("active");
        if (obj->hasProperty ("soundId")) b.soundId = obj->getProperty ("soundId").toString().toStdString();
        if (obj->hasProperty ("volume"))  b.volume  = (float) (double) obj->getProperty ("volume");
        return TrackItem (b);
    }
    if (type == "open")  return TrackItem (TrackItem::BracketOpen {});
    if (type == "close") return TrackItem (TrackItem::BracketClose {});
    if (type == "repeat")
    {
        if (! obj->hasProperty ("count")) return std::nullopt;
        return TrackItem (TrackItem::Repeat { (int) obj->getProperty ("count") });
    }
    if (type == "mod")
    {
        if (! obj->hasProperty ("p") || ! obj->hasProperty ("q")) return std::nullopt;
        return TrackItem (TrackItem::Modulation { (int) obj->getProperty ("p"),
                                                  (int) obj->getProperty ("q") });
    }
    if (type == "setbpm")
    {
        if (! obj->hasProperty ("bpmVal")) return std::nullopt;
        return TrackItem (TrackItem::SetBpm { (double) obj->getProperty ("bpmVal") });
    }
    return std::nullopt; // unknown type — skip gracefully (forward compatibility)
}

juce::var trackToVar (const TrackDraft& d)
{
    auto* obj = new juce::DynamicObject();
    obj->setProperty ("label", juce::String (d.label));
    obj->setProperty ("denom", d.denom);
    if (d.muted)  obj->setProperty ("muted",  true);
    if (d.soloed) obj->setProperty ("soloed", true);
    if (d.defaultSoundId.has_value())
        obj->setProperty ("defaultSoundId", juce::String (*d.defaultSoundId));

    juce::Array<juce::var> items;
    items.ensureStorageAllocated ((int) d.items.size());
    for (const auto& it : d.items) items.add (itemToVar (it));
    obj->setProperty ("items", items);
    return juce::var (obj);
}

std::string currentIsoTimestamp()
{
    const auto now  = std::chrono::system_clock::now();
    const auto t    = std::chrono::system_clock::to_time_t (now);
    std::tm tm {};
#if defined (_WIN32)
    gmtime_s (&tm, &t);
#else
    gmtime_r (&t, &tm);
#endif
    char buf[32];
    std::snprintf (buf, sizeof (buf), "%04d-%02d-%02dT%02d:%02d:%02dZ",
                   tm.tm_year + 1900, tm.tm_mon + 1, tm.tm_mday,
                   tm.tm_hour, tm.tm_min, tm.tm_sec);
    return buf;
}

} // namespace

std::string ProjectSerializer::serialize (const TrackBuilderState& state, const std::string& name)
{
    auto* root = new juce::DynamicObject();
    root->setProperty ("version",   ProjectSerializer::CURRENT_VERSION);
    root->setProperty ("name",      juce::String (name));
    root->setProperty ("bpm",       state.bpm);
    root->setProperty ("createdAt", juce::String (currentIsoTimestamp()));

    juce::Array<juce::var> tracks;
    tracks.ensureStorageAllocated ((int) state.tracks.size());
    for (const auto& t : state.tracks) tracks.add (trackToVar (t));
    root->setProperty ("tracks", tracks);

    return juce::JSON::toString (juce::var (root), true).toStdString();
}

TrackBuilderState ProjectSerializer::deserialize (const std::string& jsonString)
{
    juce::var v;
    auto result = juce::JSON::parse (juce::String (jsonString), v);
    if (result.failed() || ! v.isObject())
        throw DeserializationException ("Invalid JSON");
    auto* obj = v.getDynamicObject();
    if (obj == nullptr) throw DeserializationException ("Empty document");
    if (! obj->hasProperty ("version"))
        throw DeserializationException ("Missing version field");
    const int version = (int) obj->getProperty ("version");
    if (version != 1)
        throw DeserializationException ("Unsupported version " + std::to_string (version)
                                        + " — update the app to open this file");

    TrackBuilderState st;
    st.bpm              = (double) obj->getProperty ("bpm");
    st.activeTrackIndex = 0;
    st.playbackState    = PlaybackState::Stopped;
    st.inputMode        = InputMode::normal();
    st.cursorIndex.reset();

    const juce::var tracksVar = obj->getProperty ("tracks");
    if (auto* tracksArr = tracksVar.getArray())
    {
        st.tracks.reserve ((size_t) tracksArr->size());
        for (int i = 0; i < tracksArr->size(); ++i)
        {
            const juce::var tv = (*tracksArr)[i];
            if (! tv.isObject()) continue;
            auto* tobj = tv.getDynamicObject();
            if (tobj == nullptr) continue;
            TrackDraft d;
            d.id     = "track_" + std::to_string (i);
            d.label  = tobj->getProperty ("label").toString().toStdString();
            d.denom  = (int) tobj->getProperty ("denom");
            d.muted  = tobj->hasProperty ("muted")  ? (bool) tobj->getProperty ("muted")  : false;
            d.soloed = tobj->hasProperty ("soloed") ? (bool) tobj->getProperty ("soloed") : false;
            if (tobj->hasProperty ("defaultSoundId"))
                d.defaultSoundId = tobj->getProperty ("defaultSoundId").toString().toStdString();

            if (auto* itemsArr = tobj->getProperty ("items").getArray())
            {
                d.items.reserve ((size_t) itemsArr->size());
                for (int j = 0; j < itemsArr->size(); ++j)
                    if (auto it = varToItem ((*itemsArr)[j])) d.items.push_back (std::move (*it));
            }
            st.tracks.push_back (std::move (d));
        }
    }

    if (st.tracks.empty()) st.tracks.push_back (TrackBuilder::newTrackDraft (0));
    return st;
}

std::optional<TrackBuilderState> ProjectSerializer::deserializeOrNull (const std::string& jsonString)
{
    try { return deserialize (jsonString); }
    catch (...) { return std::nullopt; }
}

std::optional<std::string> ProjectSerializer::peekName (const std::string& jsonString)
{
    juce::var v;
    if (juce::JSON::parse (juce::String (jsonString), v).failed()) return std::nullopt;
    auto* obj = v.getDynamicObject();
    if (obj == nullptr || ! obj->hasProperty ("name")) return std::nullopt;
    return obj->getProperty ("name").toString().toStdString();
}

std::optional<double> ProjectSerializer::peekBpm (const std::string& jsonString)
{
    juce::var v;
    if (juce::JSON::parse (juce::String (jsonString), v).failed()) return std::nullopt;
    auto* obj = v.getDynamicObject();
    if (obj == nullptr || ! obj->hasProperty ("bpm")) return std::nullopt;
    return (double) obj->getProperty ("bpm");
}

} // namespace rhythm
