package com.octagens.plugin.model;

import org.bukkit.Material;

import java.util.Set;

/** One weighted result of an ore generator. */
public record OreEntry(Material material, double chance, int minY, int maxY, Set<String> worlds, Set<String> biomes) {
}
