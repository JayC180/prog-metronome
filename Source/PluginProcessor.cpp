#include "PluginProcessor.h"
#include "PluginEditor.h"
#include "persistence/ProjectSerializer.h"

RhythmEngineProcessor::RhythmEngineProcessor()
    : juce::AudioProcessor (BusesProperties().withOutput ("Output",
                                                          juce::AudioChannelSet::stereo(),
                                                          true)),
      audio_     (std::make_unique<rhythm::JuceAudioEngine>()),
      transport_ (std::make_unique<rhythm::Transport> (audio_.get())),
      builder_   (std::make_unique<rhythm::TrackBuilder> (120.0, transport_.get()))
{
    audio_->open();
}

RhythmEngineProcessor::~RhythmEngineProcessor()
{
    builder_->stop();
    audio_->stopAll();
    audio_->close();
}

void RhythmEngineProcessor::prepareToPlay (double sampleRate, int samplesPerBlock)
{
    audio_->prepareToPlay (sampleRate, samplesPerBlock);
}

void RhythmEngineProcessor::releaseResources()
{
    audio_->releaseResources();
}

bool RhythmEngineProcessor::isBusesLayoutSupported (const BusesLayout& layouts) const
{
    const auto& mainOut = layouts.getMainOutputChannelSet();
    if (mainOut != juce::AudioChannelSet::stereo() && mainOut != juce::AudioChannelSet::mono())
        return false;
    return true;
}

void RhythmEngineProcessor::processBlock (juce::AudioBuffer<float>& buffer, juce::MidiBuffer&)
{
    juce::ScopedNoDenormals noDenormals;
    const int numIn  = getTotalNumInputChannels();
    const int numOut = getTotalNumOutputChannels();
    for (int ch = numIn; ch < numOut; ++ch) buffer.clear (ch, 0, buffer.getNumSamples());
    buffer.clear();
    audio_->processBlock (buffer);
}

juce::AudioProcessorEditor* RhythmEngineProcessor::createEditor()
{
    return new RhythmEngineEditor (*this);
}

void RhythmEngineProcessor::getStateInformation (juce::MemoryBlock& destData)
{
    const std::string json = rhythm::ProjectSerializer::serialize (builder_->state(), projectName_);
    destData.append (json.data(), json.size());
}

void RhythmEngineProcessor::setStateInformation (const void* data, int sizeInBytes)
{
    const std::string json (static_cast<const char*> (data), (size_t) sizeInBytes);
    if (auto state = rhythm::ProjectSerializer::deserializeOrNull (json))
    {
        builder_->restoreState (*state);
        if (auto name = rhythm::ProjectSerializer::peekName (json))
            projectName_ = *name;
        wasRestoredByHost_ = true;
    }
}

bool RhythmEngineProcessor::saveToFile (const juce::File& f)
{
    const std::string json = rhythm::ProjectSerializer::serialize (builder_->state(), projectName_);
    if (! f.replaceWithText (juce::String (json)))
        return false;
    projectFile_ = f;
    isDirty_ = false;
    return true;
}

bool RhythmEngineProcessor::loadFromFile (const juce::File& f)
{
    const auto json = f.loadFileAsString().toStdString();
    auto state = rhythm::ProjectSerializer::deserializeOrNull (json);
    if (! state.has_value())
        return false;
    builder_->restoreState (*state);
    if (auto name = rhythm::ProjectSerializer::peekName (json))
        projectName_ = *name;
    projectFile_ = f;
    isDirty_ = false;
    return true;
}

juce::AudioProcessor* JUCE_CALLTYPE createPluginFilter()
{
    return new RhythmEngineProcessor();
}
