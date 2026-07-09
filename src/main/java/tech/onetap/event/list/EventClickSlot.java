package tech.onetap.event.list;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.screen.slot.SlotActionType;
import tech.onetap.event.Event;

@Getter
@Setter
@AllArgsConstructor
public class EventClickSlot extends Event {
    private int syncId;
    private int slotId;
    private int button;
    private SlotActionType actionType;
}
