package com.bettercontent.betterdeathsdoor;
import com.bettercontent.betterdeathsdoor.state.MaimType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.*;
public final class InjuryItems {
 public static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,RevivalMod.MOD_ID);
 public static final RegistryObject<Item> BALM=ITEMS.register("balm",()->new Item(new Item.Properties().stacksTo(16)));
 public static final RegistryObject<Item> SOOCHER=ITEMS.register("soocher",()->new Item(new Item.Properties()));
 // Retain the two registered IDs so existing saved stacks load; neither is a care requirement.
}
