package tech.onetap.module.settings;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class BlockListSetting extends Setting {
    public final List<Block> blocks = new ArrayList<>();
    public List<Block> candidates;

    public BlockListSetting(String name) {
        super(name);
        this.candidates = null;
    }

    public void add(Block block) {
        if (block != null && block != Blocks.AIR && !this.blocks.contains(block)) {
            this.blocks.add(block);
        }
    }

    public void remove(Block block) {
        this.blocks.remove(block);
    }

    public boolean has(Block block) {
        return this.blocks.contains(block);
    }

    public boolean isEmpty() {
        return this.blocks.isEmpty();
    }

    public List<Block> all() {
        return this.blocks;
    }

    public void clear() {
        this.blocks.clear();
    }

    public List<Block> candidates() {
        return this.candidates;
    }

    public BlockListSetting candidates(List<Block> candidates) {
        this.candidates = candidates;
        return this;
    }

    @Override
    public BlockListSetting setVisible(Supplier<Boolean> visible) {
        this.visible = visible;
        return this;
    }

    @Override
    public String getValueAsString() {
        StringBuilder sb = new StringBuilder();
        for (Block block : blocks) {
            Identifier id = Registries.BLOCK.getId(block);
            if (id != null) {
                if (sb.length() > 0) sb.append(",");
                sb.append(id.toString());
            }
        }
        return sb.toString();
    }

    @Override
    public void setValueFromString(String value) {
        blocks.clear();
        if (value == null || value.isEmpty()) return;
        String[] parts = value.split(",");
        for (String part : parts) {
            Identifier id = Identifier.tryParse(part.trim());
            if (id != null) {
                Block block = Registries.BLOCK.get(id);
                if (block != Blocks.AIR) {
                    blocks.add(block);
                }
            }
        }
    }
}
