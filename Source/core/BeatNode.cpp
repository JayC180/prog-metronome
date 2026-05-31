#include "BeatNode.h"

// All members of the BeatNode hierarchy are inline in the header.
// This translation unit exists so the build system sees one .cpp per
// header and the type information has a definite home for ABI/linker
// purposes.
namespace rhythm {} // anchor
