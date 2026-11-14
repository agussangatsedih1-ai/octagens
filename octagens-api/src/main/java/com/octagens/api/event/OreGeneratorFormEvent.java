package com.octagens.api.event;

import com.octagens.api.model.OreGeneratorType;
import org.bukkit.Material;
import org.bukkit.block.Block;

/**
 * Fired when a stone/basalt generator is about to form a block.
 * Cancelling lets vanilla behaviour continue. The result can be replaced.
 */
public final class OreGeneratorFormEvent extends CancellableOctaGensEvent {
    private final Block block;
    private final OreGeneratorType type;
    private Material result;

    public OreGeneratorFormEvent(Block block, OreGeneratorType type, Material result) {
        this.block = block;
        this.type = type;
        this.result = result;
    }

    /** @return the block that will be replaced */
    public Block getBlock() { return block; }

    /** @return generator kind */
    public OreGeneratorType getType() { return type; }

    /** @return the material that will be placed */
    public Material getResult() { return result; }

    /** Replaces the material that will be placed. */
    public void setResult(Material result) { if (result != null) this.result = result; }
}
