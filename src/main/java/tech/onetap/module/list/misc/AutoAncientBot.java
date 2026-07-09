package tech.onetap.module.list.misc;

import baritone.api.BaritoneAPI;
import com.google.common.eventbus.Subscribe;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PickaxeItem;
import net.minecraft.potion.Potions;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.module.settings.StringSetting;
import tech.onetap.util.player.other.InventoryUtil;

@ModuleInformation(moduleName = "AutoAncientBot", moduleDesc = "Автоматический майнинг древних обломков в аду", moduleCategory = ModuleCategory.MISC)
public class AutoAncientBot extends Module {

    private final ModeSetting repairMode = new ModeSetting("Починка", "Команда", "Команда");
    private final SliderSetting duraThreshold = new SliderSetting("Порог прочности, %", 30, 1, 100, 1);
    private final SliderSetting fixInterval = new SliderSetting("Таймер /fix, мин", 3, 1, 10, 1);
    private final BooleanSetting healToggle = new BooleanSetting("Авто /heal", false);
    private final SliderSetting healInterval = new SliderSetting("Таймер /heal, мин", 3, 1, 10, 1);
    private final BooleanSetting fireResToggle = new BooleanSetting("Огнестойкость", true);
    private final StringSetting skladHome = new StringSetting("Склад", "");
    private final StringSetting adHome = new StringSetting("AD дом", "");

    private enum State { MINING, EATING, REPAIRING, DRINKING, FIXING, HEALING, STORING }
    private State state = State.MINING;
    private int useCD, fireCD, netherMsg, fireMsg, repairWait, commandWait, storeWait;
    private boolean started, useKeyHeld;
    private int pickSlot, origXpSlot, repairTick, fixTicks, healTicks, storeStep, storeSubStep;
    private BlockPos chestPos;

    public void setSkladHome(String name) { skladHome.setValue(name); }
    public void setAdHome(String name) { adHome.setValue(name); }
    public String getSkladHome() { return skladHome.getValue(); }
    public String getAdHome() { return adHome.getValue(); }

    private int invToCont(int invSlot) {
        if (invSlot < 9) return invSlot + 36;
        if (invSlot == 40) return 45;
        return invSlot;
    }

    private void faceBlock(BlockPos pos) {
        Vec3d target = Vec3d.ofCenter(pos);
        Vec3d eyes = mc.player.getEyePos();
        double dx = target.x - eyes.x;
        double dy = target.y - eyes.y;
        double dz = target.z - eyes.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        mc.player.setYaw((float) Math.toDegrees(Math.atan2(dz, dx)) - 90);
        mc.player.setPitch((float) -Math.toDegrees(Math.atan2(dy, dist)));
    }

    private enum RepairStep { INIT, SWAP_PICK_OFF, WAIT_SWAP_OFF, FIND_XP, WAIT_SWAP_XP, THROWING,
        CHECK_DUR, SWAP_PICK_BACK, WAIT_SWAP_BACK, DONE }
    private RepairStep repairStep = RepairStep.DONE;

    {
        duraThreshold.visible = () -> repairMode.is("Опыт");
        fixInterval.visible = () -> repairMode.is("Команда");
        healInterval.visible = () -> healToggle.getValue();
    }

    @Override
    public void onEnable() {
        super.onEnable();
        useCD = 0; fireCD = 0; netherMsg = 0; fireMsg = 0;
        repairWait = 0; repairTick = 0; commandWait = 0; started = false; useKeyHeld = false;
        state = State.MINING; pickSlot = -1; origXpSlot = -1;
        fixTicks = (int) (fixInterval.getValue() * 1200); healTicks = (int) (healInterval.getValue() * 1200);
        storeStep = 0; storeSubStep = 0; storeWait = 0;
        repairStep = RepairStep.DONE;
        if (mc.player != null)
            mc.player.sendMessage(net.minecraft.text.Text.literal("§a[AutoAncientBot] Активирован!"), true);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        releaseUseKey();
        BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
        started = false;
    }

    private void releaseUseKey() {
        if (useKeyHeld) {
            mc.options.useKey.setPressed(false);
            useKeyHeld = false;
        }
    }

    private void msg(String s) {
        if (mc.player != null) mc.player.sendMessage(net.minecraft.text.Text.literal(s), true);
    }

    private boolean hasFireRes(ItemStack stack) {
        PotionContentsComponent pc = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (pc == null) return false;
        for (StatusEffectInstance sei : pc.getEffects()) {
            if (sei == null) continue;
            if (sei.getEffectType() == StatusEffects.FIRE_RESISTANCE) return true;
            if (sei.getEffectType().matches(StatusEffects.FIRE_RESISTANCE)) return true;
            if (sei.getEffectType().value() == StatusEffects.FIRE_RESISTANCE.value()) return true;
        }
        if (pc.potion().isPresent()) {
            if (pc.potion().get() == Potions.FIRE_RESISTANCE) return true;
            if (pc.potion().get().matches(Potions.FIRE_RESISTANCE)) return true;
            if (pc.potion().get().value() == Potions.FIRE_RESISTANCE.value()) return true;
        }
        return false;
    }

    @Subscribe
    private void onTick(EventTick e) {
        if (mc.player == null || mc.world == null || !mc.player.isAlive()) return;

        if (mc.world.getRegistryKey() == World.END) {
            if (++netherMsg % 40 == 0) msg("§c[AutoAncientBot] В Энде не работает!");
            releaseUseKey();
            BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
            started = false;
            return;
        }

        if (fireCD > 0) fireCD--; else autoFireRes();

        if (state == State.MINING) {
            if (repairMode.is("Команда") && --fixTicks <= 0) {
                BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
                state = State.FIXING;
                started = false;
                commandWait = 0;
                msg("§e[AutoAncientBot] Выполняю /fix...");
            } else if (healToggle.getValue() && --healTicks <= 0) {
                state = State.HEALING;
                started = false;
                commandWait = 0;
                msg("§e[AutoAncientBot] Лечусь...");
            }
        }

        switch (state) {
            case MINING -> doMining();
            case EATING -> doEating();
            case REPAIRING -> doRepairing();
            case DRINKING -> doDrinking();
            case FIXING -> doFixing();
            case HEALING -> doHealing();
            case STORING -> doStoring();
        }
    }

    private void doMining() {
        if (!started) {
            started = true;
            BaritoneAPI.getSettings().allowBreak.value = true;
            BaritoneAPI.getSettings().allowParkour.value = true;
            BaritoneAPI.getSettings().allowParkourPlace.value = true;
            BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().mineByName(Integer.MAX_VALUE, "ancient_debris");
            msg("§a[AutoAncientBot] Запустил копку!");
        }

        if (mc.player.getHungerManager().getFoodLevel() < 20 && !mc.player.isUsingItem()) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
            state = State.EATING;
            useCD = 0;
            msg("§e[AutoAncientBot] Ем...");
            return;
        }

        if (repairMode.is("Опыт")) {
            findPickSlot();
            if (pickSlot != -1) {
                ItemStack pick = mc.player.getInventory().getStack(pickSlot);
                int dmg = pick.getDamage();
                int maxDmg = pick.getMaxDamage();
                if (maxDmg > 0) {
                    int pct = (maxDmg - dmg) * 100 / maxDmg;
                    if (pct < duraThreshold.getIntValue()) {
                        BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
                        state = State.REPAIRING;
                        repairStep = RepairStep.INIT;
                        msg("§e[AutoAncientBot] Чиню кирку (" + pct + "%)...");
                        return;
                    }
                }
            }
        }

        if (debrisCount() >= 4 * 64) {
            if (skladHome.getValue().isEmpty() || adHome.getValue().isEmpty()) {
                msg("§c[AutoAncientBot] Не заданы дома! Используй: .autobot sklad <имя> и .autobot ad <имя>");
                return;
            }
            BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
            state = State.STORING;
            storeStep = 0;
            storeSubStep = 0;
            storeWait = 0;
            started = false;
            msg("§e[AutoAncientBot] Инвентарь полон, складываю...");
        }
    }

    private int debrisCount() {
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!s.isEmpty() && s.getItem() == Items.ANCIENT_DEBRIS) count += s.getCount();
        }
        return count;
    }

    private void doEating() {
        if (mc.player.isUsingItem()) {
            useCD++;
            if (useCD > 40) {
                releaseUseKey();
                state = State.MINING; started = false;
                msg("§c[AutoAncientBot] Таймаут еды");
            }
            return;
        }
        if (useKeyHeld) {
            releaseUseKey();
            state = State.MINING; started = false;
                msg("§a[AutoAncientBot] Поел, продолжаю...");
            return;
        }
        if (useCD > 0) { useCD++; return; }

        int slot = InventoryUtil.searchHotbarStack(s -> s.getItem() == Items.GOLDEN_CARROT);
        if (slot == -1) {
            msg("§c[AutoAncientBot] Нет золотой моркови на хотбаре!");
            state = State.MINING;
            return;
        }
        mc.player.getInventory().selectedSlot = slot;
        mc.options.useKey.setPressed(true);
        useKeyHeld = true;
        useCD = 1;
    }

    private void doDrinking() {
        if (mc.player.isUsingItem()) {
            useCD++;
            if (useCD > 50) {
                releaseUseKey();
                state = State.MINING; started = false;
                msg("§c[AutoAncientBot] Таймаут питья");
            }
            return;
        }
        if (useKeyHeld) {
            releaseUseKey();
            state = State.MINING; started = false;
                msg("§a[AutoAncientBot] Выпил, продолжаю...");
            return;
        }
        if (useCD > 0) { useCD++; return; }

        int slot = InventoryUtil.searchHotbarStack(this::hasFireRes);
        if (slot == -1) {
            msg("§c[AutoAncientBot] Нет зелья огнестойкости на хотбаре!");
            state = State.MINING;
            return;
        }
        mc.player.setPitch(90);
        mc.player.getInventory().selectedSlot = slot;
        mc.options.useKey.setPressed(true);
        useKeyHeld = true;
        useCD = 1;
    }

    private void doFixing() {
        if (++commandWait == 1) {
            findPickSlot();
            if (pickSlot != -1) mc.player.getInventory().selectedSlot = pickSlot;
            mc.getNetworkHandler().sendChatCommand("fix");
        } else if (commandWait > 20) {
            fixTicks = (int) (fixInterval.getValue() * 1200);
            state = State.MINING;
            started = false;
            msg("§a[AutoAncientBot] /fix выполнен, продолжаю...");
        }
    }

    private void doHealing() {
        if (++commandWait == 1) {
            mc.getNetworkHandler().sendChatCommand("heal");
        } else if (commandWait > 10) {
            healTicks = (int) (healInterval.getValue() * 1200);
            state = State.MINING;
            started = false;
            msg("§a[AutoAncientBot] /heal выполнен, продолжаю...");
        }
    }

    private void doStoring() {
        switch (storeStep) {
            case 0 -> { // /sethome ad
                msg("§e[AutoAncientBot] Шаг 0: /sethome " + adHome.getValue());
                mc.getNetworkHandler().sendChatCommand("sethome " + adHome.getValue());
                storeStep = 1; storeWait = 0;
            }
            case 1 -> { // wait
                if (++storeWait < 20) return;
                msg("§e[AutoAncientBot] Шаг 2: /home " + skladHome.getValue());
                storeStep = 2; storeWait = 0;
            }
            case 2 -> { // /home sklad
                mc.getNetworkHandler().sendChatCommand("home " + skladHome.getValue());
                storeStep = 3; storeWait = 0;
            }
            case 3 -> { // wait for teleport + chunk load
                if (++storeWait < 100) return;
                msg("§e[AutoAncientBot] Шаг 4: ищу сундук с рамкой");
                storeStep = 4; storeWait = 0;
            }
            case 4 -> { // find chest, cancel baritone, face
                BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
                chestPos = findChestWithFrame(4);
                if (chestPos == null) chestPos = findChestWithFrame(10);
                if (chestPos == null) chestPos = findChestWithFrame(20);
                if (chestPos == null) {
                    msg("§c[AutoAncientBot] Сундук с рамкой не найден! Пробую любой...");
                    chestPos = findAnyChest(8);
                }
                if (chestPos == null) {
                    msg("§c[AutoAncientBot] Нет сундуков! Выключаю.");
                    this.toggle(); return;
                }
                msg("§e[AutoAncientBot] Открываю сундук " + chestPos.toShortString());
                faceBlock(chestPos);
                storeStep = 5; storeWait = 0;
            }
            case 5 -> { // wait for crosshair update, then send interact packet
                if (++storeWait < 5) return;
                mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, new BlockHitResult(Vec3d.ofCenter(chestPos), Direction.UP, chestPos, false), 0));
                storeStep = 50; storeWait = 0;
            }
            case 50 -> { // wait for chest GUI
                if (++storeWait > 60) {
                    msg("§c[AutoAncientBot] Сундук не открылся, повтор...");
                    storeStep = 4; storeWait = 0; return;
                }
                if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler) {
                    msg("§a[AutoAncientBot] Сундук открыт!");
                    storeStep = 6;
                }
            }
            case 6 -> { // dump all debris via shift-click
                GenericContainerScreenHandler h = (GenericContainerScreenHandler) mc.player.currentScreenHandler;
                int chestSlots = h.getRows() * 9;
                boolean dumped = false;
                for (int i = 0; i < 36; i++) {
                    ItemStack s = mc.player.getInventory().getStack(i);
                    if (!s.isEmpty() && s.getItem() == Items.ANCIENT_DEBRIS) {
                        int slot = i >= 9 ? chestSlots + i - 9 : chestSlots + 27 + i;
                        mc.interactionManager.clickSlot(h.syncId, slot, 0, SlotActionType.QUICK_MOVE, mc.player);
                        dumped = true;
                    }
                }
                storeWait = 0;
                storeStep = dumped ? 7 : 8;
            }
            case 7 -> { // check if debris remains
                if (++storeWait < 3) return;
                if (debrisCount() > 0) {
                    mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket(0));
                    storeStep = 4; storeWait = 0; // try next chest
                } else {
                    storeStep = 8;
                }
            }
            case 8 -> { // take 4 debris to slots 9-12 (1 each)
                storeSubStep = 0;
                storeWait = 0;
                storeStep = 80;
            }
            case 80 -> { // PICKUP entire stack from chest
                if (++storeWait < 2) return;
                GenericContainerScreenHandler h = (GenericContainerScreenHandler) mc.player.currentScreenHandler;
                int chestSlots = h.getRows() * 9;
                int found = -1;
                for (int ci = 0; ci < chestSlots; ci++) {
                    if (!h.getSlot(ci).getStack().isEmpty() && h.getSlot(ci).getStack().getItem() == Items.ANCIENT_DEBRIS) {
                        found = ci; break;
                    }
                }
                if (found == -1) { storeStep = 9; storeWait = 0; break; }
                mc.interactionManager.clickSlot(h.syncId, found, 0, SlotActionType.PICKUP, mc.player);
                storeStep = 81; storeWait = 0;
            }
            case 81 -> { // right-click slot 9+storeSubStep to place 1 item
                if (++storeWait < 2) return;
                GenericContainerScreenHandler h = (GenericContainerScreenHandler) mc.player.currentScreenHandler;
                int chestSlots = h.getRows() * 9;
                int invSlot = 9 + storeSubStep;
                int containerSlot = chestSlots + invSlot - 9;
                mc.interactionManager.clickSlot(h.syncId, containerSlot, 1, SlotActionType.PICKUP, mc.player);
                storeSubStep++;
                storeWait = 0;
                if (storeSubStep < 4) {
                    // next slot
                    break;
                }
                storeStep = 82; storeWait = 0;
            }
            case 82 -> { // put remaining debris back to chest
                if (++storeWait < 2) return;
                GenericContainerScreenHandler h = (GenericContainerScreenHandler) mc.player.currentScreenHandler;
                int chestSlots = h.getRows() * 9;
                // find empty chest slot and PICKUP (deposit cursor items)
                for (int ci = 0; ci < chestSlots; ci++) {
                    if (h.getSlot(ci).getStack().isEmpty()) {
                        mc.interactionManager.clickSlot(h.syncId, ci, 0, SlotActionType.PICKUP, mc.player);
                        break;
                    }
                }
                storeStep = 9; storeWait = 0;
            }
            case 9 -> { // close framed chest, teleport back
                mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket(0));
                storeStep = 14; storeWait = 0;
            }
            case 14 -> { // /home ad
                mc.getNetworkHandler().sendChatCommand("home " + adHome.getValue());
                storeStep = 15; storeWait = 0;
            }
            case 15 -> { // wait for teleport back
                if (++storeWait < 60) return;
                storeStep = 16;
            }
            case 16 -> { // done
                state = State.MINING; started = false;
                msg("§a[AutoAncientBot] Склад готов, продолжаю...");
            }
        }
    }

    private BlockPos findChestWithFrame(int radius) {
        BlockPos p = mc.player.getBlockPos();
        Box box = new Box(p.getX() - radius, p.getY() - radius, p.getZ() - radius, p.getX() + radius, p.getY() + radius, p.getZ() + radius);
        for (ItemFrameEntity frame : mc.world.getEntitiesByClass(ItemFrameEntity.class, box, e -> true)) {
            BlockPos pos = frame.getAttachedBlockPos();
            if (mc.world.getBlockState(pos).getBlock() instanceof ChestBlock) return pos;
        }
        return null;
    }

    private BlockPos findChestWithoutFrame(int radius) {
        BlockPos p = mc.player.getBlockPos();
        Box box = new Box(p.getX() - radius, p.getY() - radius, p.getZ() - radius, p.getX() + radius, p.getY() + radius, p.getZ() + radius);
        java.util.List<ItemFrameEntity> frames = mc.world.getEntitiesByClass(ItemFrameEntity.class, box, e -> true);
        java.util.Set<BlockPos> framedChests = new java.util.HashSet<>();
        for (ItemFrameEntity frame : frames) {
            framedChests.add(frame.getAttachedBlockPos());
        }
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = mc.player.getBlockPos().add(x, y, z);
                    if (mc.world.getBlockState(pos).getBlock() instanceof ChestBlock && !framedChests.contains(pos)) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private BlockPos findAnyChest(int radius) {
        BlockPos p = mc.player.getBlockPos();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = p.add(x, y, z);
                    if (mc.world.getBlockState(pos).getBlock() instanceof ChestBlock) return pos;
                }
            }
        }
        return null;
    }

    private BlockPos findLava(int radius) {
        BlockPos p = mc.player.getBlockPos();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = p.add(x, y, z);
                    if (mc.world.getFluidState(pos).isIn(net.minecraft.registry.tag.FluidTags.LAVA)) return pos;
                }
            }
        }
        return null;
    }

    private int countItems(net.minecraft.item.Item item) {
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!s.isEmpty() && s.getItem() == item) count += s.getCount();
        }
        return count;
    }

    private void findPickSlot() {
        pickSlot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!s.isEmpty() && s.getItem() instanceof PickaxeItem) {
                pickSlot = i;
                return;
            }
        }
    }

    private int getPickPct() {
        if (pickSlot == -1) return 100;
        ItemStack pick = mc.player.getInventory().getStack(pickSlot);
        int maxDmg = pick.getMaxDamage();
        if (maxDmg <= 0) return 100;
        return (maxDmg - pick.getDamage()) * 100 / maxDmg;
    }

    private void doRepairing() {
        mc.player.setPitch(90);
        switch (repairStep) {
            case INIT -> {
                findPickSlot();
                if (pickSlot == -1) {
                    msg("§c[AutoAncientBot] Кирка не найдена!");
                    state = State.MINING; started = false;
                    return;
                }
                origXpSlot = -1;
                repairStep = RepairStep.SWAP_PICK_OFF;
            }
            case SWAP_PICK_OFF -> {
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, invToCont(pickSlot), 40, SlotActionType.SWAP, mc.player);
                mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket(0));
                repairStep = RepairStep.WAIT_SWAP_OFF;
                repairWait = 3;
            }
            case WAIT_SWAP_OFF -> {
                if (--repairWait > 0) return;
                repairStep = RepairStep.FIND_XP;
                repairWait = 2;
            }
            case FIND_XP -> {
                if (--repairWait > 0) return;
                int xpSlot = InventoryUtil.searchItem(Items.EXPERIENCE_BOTTLE);
                if (xpSlot == -1) {
                    msg("§c[AutoAncientBot] Нет больше опыта, проверяю починку...");
                    repairStep = RepairStep.CHECK_DUR;
                    return;
                }
                if (xpSlot != pickSlot) {
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, invToCont(xpSlot), pickSlot, SlotActionType.SWAP, mc.player);
                    repairStep = RepairStep.WAIT_SWAP_XP;
                    repairWait = 3;
                    return;
                }
                repairTick = 0;
                mc.player.getInventory().selectedSlot = pickSlot;
                mc.options.useKey.setPressed(true);
                useKeyHeld = true;
                repairStep = RepairStep.THROWING;
            }
            case WAIT_SWAP_XP -> {
                if (--repairWait > 0) return;
                repairTick = 0;
                mc.player.getInventory().selectedSlot = pickSlot;
                mc.options.useKey.setPressed(true);
                useKeyHeld = true;
                repairStep = RepairStep.THROWING;
            }
            case THROWING -> {
                repairTick++;
                ItemStack stack = mc.player.getInventory().getStack(pickSlot);
                if (stack.isEmpty() || stack.getItem() != Items.EXPERIENCE_BOTTLE) {
                    mc.options.useKey.setPressed(false);
                    useKeyHeld = false;
                    repairStep = RepairStep.CHECK_DUR;
                    return;
                }
                if (repairTick % 5 == 0) {
                    ItemStack offhand = mc.player.getOffHandStack();
                    if (offhand.getItem() instanceof PickaxeItem) {
                        int maxDmg = offhand.getMaxDamage();
                        int pct = (maxDmg > 0) ? (maxDmg - offhand.getDamage()) * 100 / maxDmg : 100;
                        if (pct >= 100) {
                            mc.options.useKey.setPressed(false);
                            useKeyHeld = false;
                            msg("§a[AutoAncientBot] Кирка полностью починена!");
                            repairStep = RepairStep.SWAP_PICK_BACK;
                        }
                    }
                }
            }
            case CHECK_DUR -> {
                ItemStack offhand = mc.player.getOffHandStack();
                if (!(offhand.getItem() instanceof PickaxeItem)) {
                    msg("§c[AutoAncientBot] Кирка не в оффхенде!");
                    repairStep = RepairStep.SWAP_PICK_BACK;
                    return;
                }
                int maxDmg = offhand.getMaxDamage();
                int pct = (maxDmg > 0) ? (maxDmg - offhand.getDamage()) * 100 / maxDmg : 100;
                if (pct >= 100) {
                    msg("§a[AutoAncientBot] Кирка полностью починена!");
                    repairStep = RepairStep.SWAP_PICK_BACK;
                } else if (InventoryUtil.searchItem(Items.EXPERIENCE_BOTTLE) != -1) {
                    repairStep = RepairStep.FIND_XP;
                    repairWait = 2;
                } else {
                    msg("§c[AutoAncientBot] Починка неполная (" + pct + "%), опыт кончился");
                    repairStep = RepairStep.SWAP_PICK_BACK;
                }
            }
            case SWAP_PICK_BACK -> {
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 45, pickSlot, SlotActionType.SWAP, mc.player);
                repairStep = RepairStep.WAIT_SWAP_BACK;
                repairWait = 3;
            }
            case WAIT_SWAP_BACK -> {
                if (--repairWait > 0) return;
                repairStep = RepairStep.DONE;
            }
            case DONE -> {
                state = State.MINING; started = false;
                msg("§a[AutoAncientBot] Возвращаюсь к копке...");
            }
        }
    }

    private void autoFireRes() {
        if (!fireResToggle.getValue()) return;
        if (mc.player.hasStatusEffect(StatusEffects.FIRE_RESISTANCE)) return;
        if (mc.player.isUsingItem()) return;
        state = State.DRINKING;
        useCD = 0;
        msg("§e[AutoAncientBot] Пью огнестойкость...");
    }
}
