#include "include/flu_uvc/flu_uvc_plugin_c_api.h"

#include <flutter/plugin_registrar_windows.h>

#include "flu_uvc_plugin.h"

void FluUvcPluginCApiRegisterWithRegistrar(
    FlutterDesktopPluginRegistrarRef registrar) {
  flu_uvc::FluUvcPlugin::RegisterWithRegistrar(
      flutter::PluginRegistrarManager::GetInstance()
          ->GetRegistrar<flutter::PluginRegistrarWindows>(registrar));
}
