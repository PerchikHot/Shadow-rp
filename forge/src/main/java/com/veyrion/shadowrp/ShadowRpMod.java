package com.veyrion.shadowrp;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * First playable Forge 1.20.1 milestone. Player choices and quest progress are
 * stored in server-side player NBT; no client secrets or external services.
 */
@Mod(ShadowRpMod.MOD_ID)
public final class ShadowRpMod {
    public static final String MOD_ID = "shadowrp";
    private static final String SAVE_TAG = "VeyrionShadowRp";

    public ShadowRpMod() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    private static CompoundTag progress(ServerPlayer player) {
        return player.getPersistentData().getCompound(SAVE_TAG);
    }

    private static void save(ServerPlayer player, CompoundTag state) {
        player.getPersistentData().put(SAVE_TAG, state);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> root =
            Commands.literal("shadowrp").requires(source -> source.getEntity() instanceof ServerPlayer);

        root.then(Commands.literal("start").executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            CompoundTag state = progress(player);
            if (state.getBoolean("started")) {
                context.getSource().sendSuccess(() -> Component.translatable("message.shadowrp.already_started"), false);
                return 0;
            }
            state.putBoolean("started", true);
            save(player, state);
            context.getSource().sendSuccess(() -> Component.translatable("message.shadowrp.started"), false);
            return 1;
        }));

        LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> faction = Commands.literal("faction");
        for (String choice : new String[]{"shadow", "light"}) {
            faction.then(Commands.literal(choice).executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                CompoundTag state = progress(player);
                if (!state.getBoolean("started")) {
                    context.getSource().sendFailure(Component.translatable("message.shadowrp.choose_first"));
                    return 0;
                }
                // Switching factions does not erase completed progress.
                state.putString("faction", choice);
                save(player, state);
                context.getSource().sendSuccess(() ->
                    Component.translatable("message.shadowrp.faction_" + choice), false);
                return 1;
            }));
        }
        root.then(faction);

        // A persistent, named village guide gives the prologue a visible NPC.
        // Reusing nearby guides avoids spawning an unlimited number by mistake.
        root.then(Commands.literal("guide").executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            CompoundTag state = progress(player);
            if (!state.getBoolean("started") || state.getString("faction").isEmpty()) {
                context.getSource().sendFailure(Component.translatable("message.shadowrp.guide_requires_faction"));
                return 0;
            }
            boolean existing = !player.serverLevel().getEntitiesOfClass(
                Villager.class, player.getBoundingBox().inflate(32.0),
                villager -> villager.getTags().contains("shadowrp_guide")).isEmpty();
            if (existing) {
                context.getSource().sendSuccess(() -> Component.translatable("message.shadowrp.guide_nearby"), false);
                return 1;
            }
            Villager guide = EntityType.VILLAGER.create(player.serverLevel());
            if (guide == null) return 0;
            guide.moveTo(player.getX() + 2.0, player.getY(), player.getZ() + 2.0,
                player.getYRot(), 0.0F);
            guide.addTag("shadowrp_guide");
            guide.setCustomName(Component.translatable("entity.shadowrp.guide"));
            guide.setCustomNameVisible(true);
            guide.setPersistenceRequired();
            guide.setInvulnerable(true);
            guide.setNoAi(true);
            if (!player.serverLevel().addFreshEntity(guide)) return 0;
            context.getSource().sendSuccess(() -> Component.translatable(
                !state.getBoolean("trial_complete") ? "message.shadowrp.guide_trial" :
                !state.getBoolean("oath_complete") ? "message.shadowrp.guide_oath" :
                state.getBoolean("scout_complete") ? "message.shadowrp.scout_done" :
                state.getBoolean("supplies_complete") ? "message.shadowrp.scout_request" :
                "message.shadowrp.supplies_request"), false);
            return 1;
        }));

        // A one-time chapter completion reward is persisted server-side.
        // It cannot be repeatedly claimed after relogging or dying.
        root.then(Commands.literal("oath").executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            CompoundTag state = progress(player);
            if (!state.getBoolean("trial_complete")) {
                context.getSource().sendFailure(Component.translatable("message.shadowrp.oath_locked"));
                return 0;
            }
            if (state.getBoolean("oath_complete")) {
                context.getSource().sendSuccess(() -> Component.translatable("message.shadowrp.oath_done"), false);
                return 0;
            }
            state.putBoolean("oath_complete", true);
            save(player, state);
            ItemStack reward = new ItemStack(Items.EMERALD, 1);
            if (!player.getInventory().add(reward)) player.drop(reward, false);
            context.getSource().sendSuccess(() -> Component.translatable("message.shadowrp.oath_complete"), false);
            return 1;
        }));

        root.then(Commands.literal("status").executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            CompoundTag state = progress(player);
            Component message;
            if (!state.getBoolean("started")) {
                message = Component.translatable("message.shadowrp.status_new");
            } else if (state.getString("faction").isEmpty()) {
                message = Component.translatable("message.shadowrp.status_faction");
            } else if (state.getBoolean("scout_complete")) {
                message = Component.translatable("message.shadowrp.status_scout_done", state.getString("faction"));
            } else if (state.getBoolean("supplies_complete")) {
                message = Component.translatable("message.shadowrp.status_scout",
                    state.getString("faction"), state.getInt("scout_kills"));
            } else if (state.getBoolean("oath_complete")) {
                message = Component.translatable("message.shadowrp.status_supplies", state.getString("faction"));
            } else if (state.getBoolean("trial_complete")) {
                message = Component.translatable("message.shadowrp.status_done", state.getString("faction"));
            } else {
                message = Component.translatable("message.shadowrp.status_active",
                    state.getString("faction"), state.getInt("trial_kills"));
            }
            context.getSource().sendSuccess(() -> message, false);
            return 1;
        }));
        event.getDispatcher().register(root);
    }

    /**
     * Right-click the persistent Garden Keeper to advance the first supply
     * mission. No automatic item removal, network call, or repeatable reward:
     * the player must deliberately interact with the NPC while carrying
     * eight wheat and four oak logs.
     */
    @SubscribeEvent
    public void onGuideInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof Villager guide)
                || !guide.getTags().contains("shadowrp_guide")) {
            return;
        }
        // Suppress vanilla villager trading for the story guide on both sides.
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CompoundTag state = progress(player);
        String message;
        if (!state.getBoolean("started") || state.getString("faction").isEmpty()) {
            message = "message.shadowrp.guide_requires_faction";
        } else if (!state.getBoolean("trial_complete")) {
            message = "message.shadowrp.guide_trial";
        } else if (!state.getBoolean("oath_complete")) {
            message = "message.shadowrp.guide_oath";
        } else if (state.getBoolean("scout_complete")) {
            message = "message.shadowrp.scout_done";
        } else if (state.getBoolean("supplies_complete")) {
            message = "message.shadowrp.scout_request";
        } else if (countInventoryItem(player, Items.WHEAT) < 8
                || countInventoryItem(player, Items.OAK_LOG) < 4) {
            message = "message.shadowrp.supplies_request";
        } else {
            removeInventoryItem(player, Items.WHEAT, 8);
            removeInventoryItem(player, Items.OAK_LOG, 4);
            player.getInventory().setChanged();
            state.putBoolean("supplies_complete", true);
            save(player, state);
            player.giveExperiencePoints(75);
            ItemStack reward = new ItemStack(Items.EMERALD, 2);
            if (!player.getInventory().add(reward)) player.drop(reward, false);
            message = "message.shadowrp.supplies_complete";
        }
        player.sendSystemMessage(Component.translatable(message));
    }

    private static int countInventoryItem(ServerPlayer player, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    private static void removeInventoryItem(ServerPlayer player, net.minecraft.world.item.Item item, int amount) {
        for (int slot = 0; slot < player.getInventory().getContainerSize() && amount > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.is(item)) continue;
            int taken = Math.min(stack.getCount(), amount);
            stack.shrink(taken);
            amount -= taken;
        }
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (!progress(player).getBoolean("started")) {
                player.sendSystemMessage(Component.translatable("message.shadowrp.welcome"));
            }
        }
    }

    @SubscribeEvent
    public void onClone(PlayerEvent.Clone event) {
        // Preserve faction and quest on respawn, including hardcore-compatible
        // server player recreation. Never overwrite unrelated mod NBT.
        CompoundTag previous = event.getOriginal().getPersistentData().getCompound(SAVE_TAG);
        if (!previous.isEmpty()) {
            event.getEntity().getPersistentData().put(SAVE_TAG, previous.copy());
        }
    }

    @SubscribeEvent
    public void onHostileDefeated(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)
                || !(event.getEntity() instanceof Monster)) {
            return;
        }
        CompoundTag state = progress(player);
        if (!state.getBoolean("started") || state.getString("faction").isEmpty()) return;
        // Chapter two: after delivering supplies, each Garden has its own
        // hostile target. Count only direct player kills and reward once.
        if (state.getBoolean("supplies_complete")) {
            if (state.getBoolean("scout_complete")) return;
            String faction = state.getString("faction");
            boolean target = "shadow".equals(faction)
                ? event.getEntity().getType() == EntityType.SKELETON
                : event.getEntity().getType() == EntityType.ZOMBIE;
            if (!target) return;
            // Switching factions must not carry the previous target's kills.
            if (!faction.equals(state.getString("scout_faction"))) {
                state.putString("scout_faction", faction);
                state.putInt("scout_kills", 0);
            }
            int count = Math.min(5, state.getInt("scout_kills") + 1);
            state.putInt("scout_kills", count);
            if (count == 5) {
                state.putBoolean("scout_complete", true);
                player.giveExperiencePoints(100);
                ItemStack reward = new ItemStack(Items.EMERALD, 3);
                if (!player.getInventory().add(reward)) player.drop(reward, false);
                player.sendSystemMessage(Component.translatable("message.shadowrp.scout_complete"));
            } else {
                player.sendSystemMessage(Component.translatable("message.shadowrp.scout_progress", count));
            }
            save(player, state);
            return;
        }
        if (state.getBoolean("trial_complete")) return;
        int kills = Math.min(3, state.getInt("trial_kills") + 1);
        state.putInt("trial_kills", kills);
        if (kills == 3) {
            state.putBoolean("trial_complete", true);
            player.giveExperiencePoints(50);
            player.sendSystemMessage(Component.translatable("message.shadowrp.complete"));
        } else {
            player.sendSystemMessage(Component.translatable("message.shadowrp.progress", kills));
        }
        save(player, state);
    }
}
