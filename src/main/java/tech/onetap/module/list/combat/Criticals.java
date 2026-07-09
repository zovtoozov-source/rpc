package tech.onetap.module.list.combat;

import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;

@ModuleInformation(moduleName = "Criticals", moduleCategory = ModuleCategory.COMBAT)
public class Criticals extends Module {
    // Packet criticals are intentionally disabled: Grim flags manual PlayerMoveC2SPacket
    // injections around attacks as BadPacketsZ/Post interact entity.
}
