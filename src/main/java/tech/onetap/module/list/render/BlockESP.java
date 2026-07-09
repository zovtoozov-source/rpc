package tech.onetap.module.list.render;

import com.google.common.eventbus.Subscribe;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Matrix4f;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import tech.onetap.event.list.EventPacket;
import tech.onetap.event.list.EventTick;
import tech.onetap.event.list.EventWorldRender;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BlockListSetting;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.ColorSetting;
import tech.onetap.util.render.providers.ColorProvider;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@ModuleInformation(moduleName = "BlockESP", moduleDesc = "Поиск блоков/руд сквозь стены", moduleCategory = ModuleCategory.RENDER)
public class BlockESP extends Module {

    private static final int FILL_COLOR = 0x40000000;
    private static final int OUTLINE_COLOR = 0xFF000000;
    private static final int TRACER_COLOR = 0xC0000000;
    private static final int RGB_MASK = 0xFFFFFF;
    private static final int SECTION_SIZE = 16;

    private static final float[] FULL = {0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F};
    private static final Map<BlockState, float[]> SHAPE_CACHE = new ConcurrentHashMap<>();

    protected final ColorSetting color;
    protected final BooleanSetting tracers;
    protected final BlockListSetting search;

    private final Map<Long, List<Pos>> chunks;
    private final ExecutorService worker;
    protected volatile Set<Block> targets;
    private int lastTargetsHash;
    private RegistryKey<World> lastDim;

    public BlockESP() {
        this("BlockESP", "Поиск блоков/руд сквозь стены");
    }

    public BlockESP(String name, String description) {
        super();
        this.color = new ColorSetting("Color", ColorProvider.rgba(0, 255, 204, 255));
        this.tracers = new BooleanSetting("Tracers", false);
        this.search = new BlockListSetting("Search");
        this.chunks = new ConcurrentHashMap<>();
        this.worker = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "BlockESP");
            thread.setDaemon(true);
            return thread;
        });
        this.targets = new HashSet<>();
        this.lastTargetsHash = 0;
        seedDefaults();
        this.search.candidates(new ArrayList<>(this.search.all()));
        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            if (isEnabled() && !this.targets.isEmpty()) {
                searchChunk(chunk);
            }
        });
        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) ->
                this.chunks.remove(chunk.getPos().toLong()));
    }

    public void seedDefaults() {
        if (this.search.isEmpty()) {
            this.search.add(Blocks.CHEST);
            this.search.add(Blocks.TRAPPED_CHEST);
            this.search.add(Blocks.ENDER_CHEST);
            this.search.add(Blocks.SHULKER_BOX);
            this.search.add(Blocks.GOLD_ORE);
            this.search.add(Blocks.DEEPSLATE_GOLD_ORE);
            this.search.add(Blocks.NETHER_GOLD_ORE);
            this.search.add(Blocks.GOLD_BLOCK);
            this.search.add(Blocks.ANCIENT_DEBRIS);
            this.search.add(Blocks.NETHERITE_BLOCK);
            this.search.add(Blocks.DIAMOND_ORE);
            this.search.add(Blocks.DEEPSLATE_DIAMOND_ORE);
            this.search.add(Blocks.DIAMOND_BLOCK);
            this.search.add(Blocks.SPAWNER);
        }
    }

    public boolean isTarget(BlockState state) {
        return this.targets.contains(state.getBlock());
    }

    public int targetsHash() {
        int hash = 1;
        for (Block block : this.search.all()) {
            hash = 31 * hash + System.identityHashCode(block);
        }
        return hash;
    }

    public boolean outOfRange(int chunkX, int chunkZ) {
        if (mc.player == null) {
            return true;
        }
        int viewDistance = mc.options.getClampedViewDistance() + 1;
        int playerChunkX = mc.player.getChunkPos().x;
        int playerChunkZ = mc.player.getChunkPos().z;
        return chunkX > playerChunkX + viewDistance
                || chunkX < playerChunkX - viewDistance
                || chunkZ > playerChunkZ + viewDistance
                || chunkZ < playerChunkZ - viewDistance;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        this.targets = new HashSet<>(this.search.all());
        this.lastTargetsHash = targetsHash();
        reactivate();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        this.chunks.clear();
    }

    @Subscribe
    private void onTick(EventTick e) {
        if (mc.world == null || mc.player == null) {
            return;
        }
        int hash = targetsHash();
        if (hash != this.lastTargetsHash) {
            this.lastTargetsHash = hash;
            this.targets = new HashSet<>(this.search.all());
            reactivate();
        }
        RegistryKey<World> dimension = mc.world.getRegistryKey();
        if (this.lastDim != null && !this.lastDim.equals(dimension)) {
            reactivate();
        }
        this.lastDim = dimension;
    }

    public void reactivate() {
        this.chunks.clear();
        if (mc.world == null || mc.player == null) {
            return;
        }
        this.lastDim = mc.world.getRegistryKey();
        int viewDistance = mc.options.getClampedViewDistance();
        int playerChunkX = mc.player.getChunkPos().x;
        int playerChunkZ = mc.player.getChunkPos().z;
        for (int dx = -viewDistance; dx <= viewDistance; dx++) {
            for (int dz = -viewDistance; dz <= viewDistance; dz++) {
                searchChunk(mc.world.getChunk(playerChunkX + dx, playerChunkZ + dz));
            }
        }
    }

    public void searchChunk(WorldChunk chunk) {
        this.worker.submit(() -> {
            if (!isEnabled() || mc.world == null) {
                return;
            }
            int chunkX = chunk.getPos().x;
            int chunkZ = chunk.getPos().z;
            if (outOfRange(chunkX, chunkZ)) {
                return;
            }
            Set<Block> currentTargets = this.targets;
            if (currentTargets.isEmpty()) {
                return;
            }
            List<Pos> found = new ArrayList<>();
            ChunkSection[] sections = chunk.getSectionArray();
            int baseX = chunkX << 4;
            int baseZ = chunkZ << 4;
            for (int index = 0; index < sections.length; index++) {
                ChunkSection section = sections[index];
                if (section == null || section.isEmpty()) {
                    continue;
                }
                if (!section.hasAny(this::isTarget)) {
                    continue;
                }
                int baseY = chunk.sectionIndexToCoord(index) << 4;
                for (int x = 0; x < SECTION_SIZE; x++) {
                    for (int y = 0; y < SECTION_SIZE; y++) {
                        for (int z = 0; z < SECTION_SIZE; z++) {
                            BlockState state = section.getBlockState(x, y, z);
                            if (isTarget(state)) {
                                found.add(makePos(state, baseX + x, baseY + y, baseZ + z));
                            }
                        }
                    }
                }
            }
            long key = ChunkPos.toLong(chunkX, chunkZ);
            if (!found.isEmpty()) {
                this.chunks.put(key, found);
            } else {
                this.chunks.remove(key);
            }
        });
    }

    @Subscribe
    private void onPacket(EventPacket e) {
        if (!isEnabled() || this.targets.isEmpty() || mc.world == null) return;
        if (e.getType() == tech.onetap.event.list.EventPacket.Type.SEND) return;

        if (e.getPacket() instanceof BlockUpdateS2CPacket p) {
            BlockPos pos = p.getPos();
            BlockState oldState = mc.world.getBlockState(pos);
            BlockState newState = p.getState();
            onBlockChange(pos, oldState, newState);
        } else if (e.getPacket() instanceof ChunkDeltaUpdateS2CPacket p) {
            p.visitUpdates((pos, newState) -> {
                BlockState oldState = mc.world.getBlockState(pos);
                onBlockChange(pos, oldState, newState);
            });
        }
    }

    private void onBlockChange(BlockPos pos, BlockState oldState, BlockState newState) {
        if (oldState == newState) return;
        boolean wasTarget = isTarget(oldState);
        boolean isTargetNow = isTarget(newState);
        if (wasTarget == isTargetNow) return;

        int blockX = pos.getX();
        int blockY = pos.getY();
        int blockZ = pos.getZ();
        long key = ChunkPos.toLong(blockX >> 4, blockZ >> 4);
        this.worker.submit(() -> {
            if (isTargetNow) {
                if (outOfRange(blockX >> 4, blockZ >> 4)) return;
                this.chunks.computeIfAbsent(key, ignored -> new ArrayList<>())
                        .add(makePos(newState, blockX, blockY, blockZ));
            } else {
                List<Pos> list = this.chunks.get(key);
                if (list != null) {
                    list.removeIf(entry -> entry.x() == blockX && entry.y() == blockY && entry.z() == blockZ);
                    if (list.isEmpty()) this.chunks.remove(key);
                }
            }
        });
    }

    @Subscribe
    private void onWorldRender(EventWorldRender e) {
        if (this.chunks.isEmpty() || mc.player == null) {
            return;
        }
        int baseColor = this.color.getValue();
        int fillColor = FILL_COLOR | (baseColor & RGB_MASK);
        int outlineColor = OUTLINE_COLOR | (baseColor & RGB_MASK);
        int tracerColor = TRACER_COLOR | (baseColor & RGB_MASK);
        boolean drawTracers = this.tracers.getValue();
        List<Long> stale = null;
        Vec3d cam = mc.gameRenderer.getCamera().getPos();
        MatrixStack m = e.getMatrixStack();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        float af = ((fillColor >> 24) & 0xFF) / 255f;
        float rf = ((fillColor >> 16) & 0xFF) / 255f;
        float gf = ((fillColor >> 8) & 0xFF) / 255f;
        float bf = (fillColor & 0xFF) / 255f;

        float ao = ((outlineColor >> 24) & 0xFF) / 255f;
        float ro = ((outlineColor >> 16) & 0xFF) / 255f;
        float go = ((outlineColor >> 8) & 0xFF) / 255f;
        float bo = (outlineColor & 0xFF) / 255f;

        float at = ((tracerColor >> 24) & 0xFF) / 255f;
        float rt = ((tracerColor >> 16) & 0xFF) / 255f;
        float gt = ((tracerColor >> 8) & 0xFF) / 255f;
        float bt = (tracerColor & 0xFF) / 255f;

        Matrix4f matrix = m.peek().getPositionMatrix();

        for (Map.Entry<Long, List<Pos>> entry : this.chunks.entrySet()) {
            long key = entry.getKey();
            ChunkPos chunkPos = new ChunkPos(key);
            if (outOfRange(chunkPos.x, chunkPos.z)) {
                if (stale == null) {
                    stale = new ArrayList<>();
                }
                stale.add(key);
                continue;
            }
            for (Pos pos : entry.getValue()) {
                double minX = pos.x() + pos.bx1() - cam.x;
                double minY = pos.y() + pos.by1() - cam.y;
                double minZ = pos.z() + pos.bz1() - cam.z;
                double maxX = pos.x() + pos.bx2() - cam.x;
                double maxY = pos.y() + pos.by2() - cam.y;
                double maxZ = pos.z() + pos.bz2() - cam.z;

                {
                    BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
                    buffer.vertex(matrix, (float) minX, (float) minY, (float) minZ).color(rf, gf, bf, af);
                    buffer.vertex(matrix, (float) maxX, (float) minY, (float) minZ).color(rf, gf, bf, af);
                    buffer.vertex(matrix, (float) maxX, (float) minY, (float) maxZ).color(rf, gf, bf, af);
                    buffer.vertex(matrix, (float) minX, (float) minY, (float) maxZ).color(rf, gf, bf, af);

                    buffer.vertex(matrix, (float) minX, (float) maxY, (float) minZ).color(rf, gf, bf, af);
                    buffer.vertex(matrix, (float) minX, (float) maxY, (float) maxZ).color(rf, gf, bf, af);
                    buffer.vertex(matrix, (float) maxX, (float) maxY, (float) maxZ).color(rf, gf, bf, af);
                    buffer.vertex(matrix, (float) maxX, (float) maxY, (float) minZ).color(rf, gf, bf, af);

                    buffer.vertex(matrix, (float) minX, (float) minY, (float) minZ).color(rf, gf, bf, af);
                    buffer.vertex(matrix, (float) minX, (float) maxY, (float) minZ).color(rf, gf, bf, af);
                    buffer.vertex(matrix, (float) maxX, (float) maxY, (float) minZ).color(rf, gf, bf, af);
                    buffer.vertex(matrix, (float) maxX, (float) minY, (float) minZ).color(rf, gf, bf, af);

                    buffer.vertex(matrix, (float) minX, (float) minY, (float) maxZ).color(rf, gf, bf, af);
                    buffer.vertex(matrix, (float) maxX, (float) minY, (float) maxZ).color(rf, gf, bf, af);
                    buffer.vertex(matrix, (float) maxX, (float) maxY, (float) maxZ).color(rf, gf, bf, af);
                    buffer.vertex(matrix, (float) minX, (float) maxY, (float) maxZ).color(rf, gf, bf, af);

                    buffer.vertex(matrix, (float) minX, (float) minY, (float) minZ).color(rf, gf, bf, af);
                    buffer.vertex(matrix, (float) minX, (float) minY, (float) maxZ).color(rf, gf, bf, af);
                    buffer.vertex(matrix, (float) minX, (float) maxY, (float) maxZ).color(rf, gf, bf, af);
                    buffer.vertex(matrix, (float) minX, (float) maxY, (float) minZ).color(rf, gf, bf, af);

                    buffer.vertex(matrix, (float) maxX, (float) minY, (float) minZ).color(rf, gf, bf, af);
                    buffer.vertex(matrix, (float) maxX, (float) maxY, (float) minZ).color(rf, gf, bf, af);
                    buffer.vertex(matrix, (float) maxX, (float) maxY, (float) maxZ).color(rf, gf, bf, af);
                    buffer.vertex(matrix, (float) maxX, (float) minY, (float) maxZ).color(rf, gf, bf, af);
                    BufferRenderer.drawWithGlobalProgram(buffer.end());
                }

                {
                    RenderSystem.lineWidth(1.5F);
                    BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
                    buffer.vertex(matrix, (float) minX, (float) minY, (float) minZ).color(ro, go, bo, ao);
                    buffer.vertex(matrix, (float) maxX, (float) minY, (float) minZ).color(ro, go, bo, ao);
                    buffer.vertex(matrix, (float) maxX, (float) minY, (float) minZ).color(ro, go, bo, ao);
                    buffer.vertex(matrix, (float) maxX, (float) minY, (float) maxZ).color(ro, go, bo, ao);
                    buffer.vertex(matrix, (float) maxX, (float) minY, (float) maxZ).color(ro, go, bo, ao);
                    buffer.vertex(matrix, (float) minX, (float) minY, (float) maxZ).color(ro, go, bo, ao);
                    buffer.vertex(matrix, (float) minX, (float) minY, (float) maxZ).color(ro, go, bo, ao);
                    buffer.vertex(matrix, (float) minX, (float) minY, (float) minZ).color(ro, go, bo, ao);

                    buffer.vertex(matrix, (float) minX, (float) maxY, (float) minZ).color(ro, go, bo, ao);
                    buffer.vertex(matrix, (float) maxX, (float) maxY, (float) minZ).color(ro, go, bo, ao);
                    buffer.vertex(matrix, (float) maxX, (float) maxY, (float) minZ).color(ro, go, bo, ao);
                    buffer.vertex(matrix, (float) maxX, (float) maxY, (float) maxZ).color(ro, go, bo, ao);
                    buffer.vertex(matrix, (float) maxX, (float) maxY, (float) maxZ).color(ro, go, bo, ao);
                    buffer.vertex(matrix, (float) minX, (float) maxY, (float) maxZ).color(ro, go, bo, ao);
                    buffer.vertex(matrix, (float) minX, (float) maxY, (float) maxZ).color(ro, go, bo, ao);
                    buffer.vertex(matrix, (float) minX, (float) maxY, (float) minZ).color(ro, go, bo, ao);

                    buffer.vertex(matrix, (float) minX, (float) minY, (float) minZ).color(ro, go, bo, ao);
                    buffer.vertex(matrix, (float) minX, (float) maxY, (float) minZ).color(ro, go, bo, ao);
                    buffer.vertex(matrix, (float) maxX, (float) minY, (float) minZ).color(ro, go, bo, ao);
                    buffer.vertex(matrix, (float) maxX, (float) maxY, (float) minZ).color(ro, go, bo, ao);
                    buffer.vertex(matrix, (float) maxX, (float) minY, (float) maxZ).color(ro, go, bo, ao);
                    buffer.vertex(matrix, (float) maxX, (float) maxY, (float) maxZ).color(ro, go, bo, ao);
                    buffer.vertex(matrix, (float) minX, (float) minY, (float) maxZ).color(ro, go, bo, ao);
                    buffer.vertex(matrix, (float) minX, (float) maxY, (float) maxZ).color(ro, go, bo, ao);
                    BufferRenderer.drawWithGlobalProgram(buffer.end());
                    RenderSystem.lineWidth(1.0F);
                }

                if (drawTracers) {
                    double cx = pos.x() + 0.5 - cam.x;
                    double cy = pos.y() + 0.5 - cam.y;
                    double cz = pos.z() + 0.5 - cam.z;

                    Vec3d look = mc.player.getRotationVecClient();
                    double startX = look.x * 0.1;
                    double startY = look.y * 0.1 + (mc.player.getStandingEyeHeight() - cam.y);
                    double startZ = look.z * 0.1;

                    BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
                    buf.vertex(matrix, (float) startX, (float) startY, (float) startZ).color(rt, gt, bt, at);
                    buf.vertex(matrix, (float) cx, (float) cy, (float) cz).color(rt, gt, bt, at);
                    BufferRenderer.drawWithGlobalProgram(buf.end());
                }
            }
        }
        if (stale != null) {
            for (Long key : stale) {
                this.chunks.remove(key);
            }
        }

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    public Pos makePos(BlockState state, int x, int y, int z) {
        float[] shape = SHAPE_CACHE.computeIfAbsent(state, cachedState -> {
            try {
                VoxelShape voxelShape = cachedState.getOutlineShape(mc.world, new BlockPos(x, y, z));
                if (!voxelShape.isEmpty()) {
                    Box box = voxelShape.getBoundingBox();
                    return new float[]{
                            (float) box.minX, (float) box.minY, (float) box.minZ,
                            (float) box.maxX, (float) box.maxY, (float) box.maxZ
                    };
                }
            } catch (Throwable ignored) {
                return FULL;
            }
            return FULL;
        });
        return new Pos(x, y, z, shape[0], shape[1], shape[2], shape[3], shape[4], shape[5]);
    }

    public record Pos(int x, int y, int z, float bx1, float by1, float bz1, float bx2, float by2, float bz2) {
    }
}
