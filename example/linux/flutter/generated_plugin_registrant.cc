//
//  Generated file. Do not edit.
//

// clang-format off

#include "generated_plugin_registrant.h"

#include <file_selector_linux/file_selector_plugin.h>
#include <flu_uvc/flu_uvc_plugin.h>
#include <m2pos/m2pos_plugin.h>

void fl_register_plugins(FlPluginRegistry* registry) {
  g_autoptr(FlPluginRegistrar) file_selector_linux_registrar =
      fl_plugin_registry_get_registrar_for_plugin(registry, "FileSelectorPlugin");
  file_selector_plugin_register_with_registrar(file_selector_linux_registrar);
  g_autoptr(FlPluginRegistrar) flu_uvc_registrar =
      fl_plugin_registry_get_registrar_for_plugin(registry, "FluUvcPlugin");
  flu_uvc_plugin_register_with_registrar(flu_uvc_registrar);
  g_autoptr(FlPluginRegistrar) m2pos_registrar =
      fl_plugin_registry_get_registrar_for_plugin(registry, "M2posPlugin");
  m2pos_plugin_register_with_registrar(m2pos_registrar);
}
