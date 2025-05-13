#ifndef FLUTTER_PLUGIN_FLU_UVC_PLUGIN_H_
#define FLUTTER_PLUGIN_FLU_UVC_PLUGIN_H_

#include <flutter/method_channel.h>
#include <flutter/plugin_registrar_windows.h>

#include <memory>

namespace flu_uvc {

class FluUvcPlugin : public flutter::Plugin {
 public:
  static void RegisterWithRegistrar(flutter::PluginRegistrarWindows *registrar);

  FluUvcPlugin();

  virtual ~FluUvcPlugin();

  // Disallow copy and assign.
  FluUvcPlugin(const FluUvcPlugin&) = delete;
  FluUvcPlugin& operator=(const FluUvcPlugin&) = delete;

  // Called when a method is called on this plugin's channel from Dart.
  void HandleMethodCall(
      const flutter::MethodCall<flutter::EncodableValue> &method_call,
      std::unique_ptr<flutter::MethodResult<flutter::EncodableValue>> result);
};

}  // namespace flu_uvc

#endif  // FLUTTER_PLUGIN_FLU_UVC_PLUGIN_H_
