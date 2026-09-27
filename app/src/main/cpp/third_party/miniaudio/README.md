# miniaudio

Vendored from `mackron/miniaudio` release 0.11.25 at commit
`9634bedb5b5a2ca38c1ee7108a9358a4e233f14d`

- Upstream: https://github.com/mackron/miniaudio
- Header SHA-256: `AC7AF4DE748B7E26B777F37E01CEE313A308A7296A3EB080E2906B320CC55C89`
- License: public domain or MIT-0; see `LICENSE`.
- Enabled here: in-memory WAV, MP3, and FLAC decoding plus format, channel, and
  sample-rate conversion.
- Disabled here: device I/O, resource manager, node graph, engine, encoding,
  generation, and threading APIs.

The current ver may have decoder issues: https://github.com/mackron/miniaudio/issues/1123. So before updating the header, review upstream decoder changes and malformed-input reports. Imported files are bounded by `SampleStore::MAX_ENCODED_BYTES`, and decoded output is bounded in `SampleStore::decodeAudio()`.
