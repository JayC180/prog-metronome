#include "Transport.h"

#include <chrono>
#include <sstream>

namespace rhythm
{

Transport::Transport (AudioEngine* engine)
    : audioEngine_ (engine),
      soundMap_    (std::make_shared<SoundMap>()) {}

Transport::~Transport()
{
    stop();
}

int64_t Transport::nowNanos()
{
    return std::chrono::duration_cast<std::chrono::nanoseconds> (
               std::chrono::steady_clock::now().time_since_epoch())
        .count();
}

void Transport::start()
{
    if (isPlaying_.load()) return;

    originNanos_        = nowNanos();
    scheduledUpToNanos_ = originNanos_;
    isPlaying_.store (true);
    for (auto& c : clocks_) c->reset();

    schedulerThread_  = std::thread (&Transport::runScheduler,  this);
    dispatcherThread_ = std::thread (&Transport::runDispatcher, this);
}

void Transport::stop()
{
    if (! isPlaying_.exchange (false)) return;

    if (schedulerThread_.joinable())  schedulerThread_.join();
    if (dispatcherThread_.joinable()) dispatcherThread_.join();

    {
        std::lock_guard<std::mutex> lock (queueMutex_);
        // clear queue
        EventQueue empty;
        std::swap (eventQueue_, empty);
    }
    for (auto& c : clocks_) c->reset();
}

void Transport::updateTracks (std::vector<InterpretResult> results)
{
    const bool wasPlaying = isPlaying_.load();
    if (wasPlaying) stop();

    std::lock_guard<std::mutex> lock (resultsMutex_);
    results_ = std::move (results);
    clocks_.clear();
    clocks_.reserve (results_.size());
    for (auto& r : results_)
        clocks_.push_back (std::make_unique<StreamClock> (r));
}

void Transport::setTrackFlags (const std::string& trackId, bool muted, bool soloed)
{
    std::lock_guard<std::mutex> lock (resultsMutex_);
    for (auto& r : results_)
    {
        if (r.trackId == trackId)
        {
            r.muted  = muted;
            r.soloed = soloed;
            return;
        }
    }
}

void Transport::fillQueueUntil (int64_t horizonNanos)
{
    std::lock_guard<std::mutex> lock (queueMutex_);
    for (auto& c : clocks_)
        if (c->isRunning())
            c->fillUntil (horizonNanos, originNanos_, eventQueue_);
}

void Transport::runScheduler()
{
    while (isPlaying_.load())
    {
        const int64_t horizon = nowNanos() + LOOKAHEAD_NANOS;
        if (scheduledUpToNanos_ < horizon)
        {
            fillQueueUntil (horizon);
            scheduledUpToNanos_ = horizon;
        }
        std::this_thread::sleep_for (std::chrono::milliseconds (SCHEDULER_INTERVAL_MS));
    }
}

void Transport::runDispatcher()
{
    // any-soloed check — recompute each pass
    while (isPlaying_.load())
    {
        const int64_t now = nowNanos();
        std::vector<ScheduledEvent> toFire;

        {
            std::lock_guard<std::mutex> lock (queueMutex_);
            while (! eventQueue_.empty() && eventQueue_.top().absoluteNanos <= now)
            {
                toFire.push_back (eventQueue_.top());
                eventQueue_.pop();
            }
        }

        // Snapshot per-track flags under the lock so live M/S toggles
        // (Transport::setTrackFlags) take effect on the next dispatch pass.
        struct Flags { bool muted; bool soloed; };
        std::vector<std::pair<std::string, Flags>> flagSnapshot;
        bool anySoloed = false;
        {
            std::lock_guard<std::mutex> lock (resultsMutex_);
            flagSnapshot.reserve (results_.size());
            for (const auto& r : results_)
            {
                flagSnapshot.push_back ({ r.trackId, { r.muted, r.soloed } });
                if (r.soloed) anySoloed = true;
            }
        }

        for (const auto& ev : toFire)
        {
            const Flags* flags = nullptr;
            for (const auto& [tid, f] : flagSnapshot)
                if (tid == ev.trackId) { flags = &f; break; }

            const bool audible = anySoloed ? (flags != nullptr && flags->soloed)
                                           : (flags != nullptr && ! flags->muted);

            AudioEngine* engine = audioEngine_.load();
            if (ev.soundId.has_value() && audible && engine != nullptr)
                engine->trigger (*ev.soundId, ev.volume, ev.absoluteNanos);

            if (onFired_) onFired_ (ev);
        }

        std::this_thread::sleep_for (std::chrono::nanoseconds (DISPATCHER_SPIN_NANOS));
    }
}

std::string Transport::debugState() const
{
    std::ostringstream os;
    os << "Transport(playing=" << (isPlaying_.load() ? "true" : "false")
       << ", queued=" << eventQueue_.size() << ")\n";
    for (const auto& c : clocks_)
        os << "  " << c->debugState() << "\n";
    return os.str();
}

} // namespace rhythm
