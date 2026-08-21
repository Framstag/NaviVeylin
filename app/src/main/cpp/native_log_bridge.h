#ifndef NAVIVEYLIN_NATIVE_LOG_BRIDGE_H
#define NAVIVEYLIN_NATIVE_LOG_BRIDGE_H

// NaviVeylin-owned logging bridge: forwards libosmscout's platform
// independent osmscout::log output to Android Logcat. This is app code,
// NOT part of the libosmscout submodule — the only place allowed to use
// Android logging APIs.

namespace naviveylin {

void InstallAndroidLogBridge();

}  // namespace naviveylin

#endif  // NAVIVEYLIN_NATIVE_LOG_BRIDGE_H
