#pragma once

#include "../builder/TrackBuilderState.h"
#include <optional>
#include <stdexcept>
#include <string>

namespace rhythm
{

// Serializes and deserializes the full project state to/from JSON (.rhy files).
//
// Design principles:
//   - version field is mandatory — always checked first
//   - format is flat and human-readable — users can edit JSON directly
//   - soundIds are stored as-is — missing sounds play as silence on load
//   - runtime-internal fields (track IDs, cursor position) are NOT saved;
//     they are regenerated fresh on load
//   - migration handles older versions cleanly
class ProjectSerializer
{
public:
    static constexpr int         CURRENT_VERSION  = 1;
    static constexpr const char* FILE_EXTENSION   = "rhy";

    static std::string                serialize   (const TrackBuilderState& state,
                                                   const std::string& name);
    static TrackBuilderState          deserialize (const std::string& jsonString);
    static std::optional<TrackBuilderState>
                                       deserializeOrNull (const std::string& jsonString);

    static std::optional<std::string> peekName (const std::string& jsonString);
    static std::optional<double>      peekBpm  (const std::string& jsonString);
};

class DeserializationException : public std::runtime_error
{
public:
    using std::runtime_error::runtime_error;
};

} // namespace rhythm
