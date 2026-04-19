package net.zoogle.levelrpg.data;

import net.minecraft.resources.ResourceLocation;

// Minimal representation of an XP curve; initial support for a simple polynomial: needed = b*L + c
public record XpCurveDefinition(ResourceLocation id, String type, double b, double c) {
}
