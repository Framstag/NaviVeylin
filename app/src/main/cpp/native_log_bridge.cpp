#include "native_log_bridge.h"

#include <jni.h>
#include <android/log.h>

#include <mutex>
#include <string>

#include <osmscout/log/Logger.h>

namespace naviveylin {

namespace {

constexpr const char* kLogTag = "NaviVeylin";

/**
 * Destination that buffers one log line and flushes it to Android Logcat
 * at PrintLn(). The owning Logger sets the Android priority per line.
 */
class AndroidLogDestination : public osmscout::Logger::Destination
{
public:
  void SetPriority(int priority)
  {
    std::lock_guard<std::mutex> lock(mutex_);
    priority_ = priority;
  }

  void Print(const std::string& value) override { Append(value); }
  void Print(const std::string_view& value) override { Append(value); }
  void Print(const char* value) override { Append(value); }
  void Print(bool value) override { Append(value ? "true" : "false"); }
  void Print(short value) override { Append(std::to_string(value)); }
  void Print(unsigned short value) override { Append(std::to_string(value)); }
  void Print(int value) override { Append(std::to_string(value)); }
  void Print(unsigned int value) override { Append(std::to_string(value)); }
  void Print(long value) override { Append(std::to_string(value)); }
  void Print(unsigned long value) override { Append(std::to_string(value)); }
  void Print(long long value) override { Append(std::to_string(value)); }
  void Print(unsigned long long value) override { Append(std::to_string(value)); }

  void PrintLn() override
  {
    std::string line;
    int priority;
    {
      std::lock_guard<std::mutex> lock(mutex_);
      line = std::move(buffer_);
      priority = priority_;
    }
    __android_log_print(priority, kLogTag, "%s", line.c_str());
  }

private:
  template <typename T>
  void Append(const T& value)
  {
    std::lock_guard<std::mutex> lock(mutex_);
    buffer_.append(value);
  }

  std::mutex mutex_;
  std::string buffer_;
  int priority_ = ANDROID_LOG_DEBUG;
};

/**
 * osmscout::log sink that maps log levels to Logcat priorities.
 */
class AndroidLogLogger : public osmscout::Logger
{
public:
  osmscout::Logger::Line Log(osmscout::Logger::Level level) override
  {
    switch (level) {
      case osmscout::Logger::DEBUG: destination_.SetPriority(ANDROID_LOG_DEBUG); break;
      case osmscout::Logger::INFO: destination_.SetPriority(ANDROID_LOG_INFO); break;
      case osmscout::Logger::WARN: destination_.SetPriority(ANDROID_LOG_WARN); break;
      case osmscout::Logger::ERROR: destination_.SetPriority(ANDROID_LOG_ERROR); break;
    }
    return osmscout::Logger::Line(destination_);
  }

private:
  AndroidLogDestination destination_;
};

}  // namespace

void InstallAndroidLogBridge()
{
  osmscout::log.SetLogger(std::make_shared<AndroidLogLogger>());
}

}  // namespace naviveylin

extern "C" JNIEXPORT void JNICALL
Java_com_naviveylin_NativeLogBridge_installNative(JNIEnv* /*env*/, jobject /*thiz*/)
{
  naviveylin::InstallAndroidLogBridge();
}
