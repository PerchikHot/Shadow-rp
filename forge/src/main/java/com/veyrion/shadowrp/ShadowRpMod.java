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
import net.minecraft.world.level.Level;
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

        // Discoverability without changing the player's saved faction or quests.
        root.then(Commands.literal("help").executes(context -> {
            context.getSource().sendSuccess(() ->
                Component.translatable("message.shadowrp.help"), false);
            return 1;
        }));

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
                // The oath commits a player to their faction. Do not let
                // completed scouting rewards carry over to the opposite side.
                if (state.getBoolean("oath_complete") &&
                    !choice.equals(state.getString("faction"))) {
                    context.getSource().sendFailure(Component.translatable("message.shadowrp.faction_locked"));
                    return 0;
                }
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
            final String factionId = state.getString("faction");
            boolean existing = !player.serverLevel().getEntitiesOfClass(
                Villager.class, player.getBoundingBox().inflate(32.0),
                villager -> villager.getTags().contains("shadowrp_guide") &&
                    (!villager.getTags().contains("shadowrp_guide_shadow") &&
                     !villager.getTags().contains("shadowrp_guide_light") ||
                     villager.getTags().contains("shadowrp_guide_" + factionId))).isEmpty();
            if (existing) {
                context.getSource().sendSuccess(() -> Component.translatable("message.shadowrp.guide_nearby"), false);
                return 1;
            }
            Villager guide = EntityType.VILLAGER.create(player.serverLevel());
            if (guide == null) return 0;
            guide.moveTo(player.getX() + 2.0, player.getY(), player.getZ() + 2.0,
                player.getYRot(), 0.0F);
            guide.addTag("shadowrp_guide");
            guide.addTag("shadowrp_guide_" + factionId);
            guide.setCustomName(Component.translatable("entity.shadowrp.guide_" + factionId));
            guide.setCustomNameVisible(true);
            guide.setPersistenceRequired();
            guide.setInvulnerable(true);
            guide.setNoAi(true);
            if (!player.serverLevel().addFreshEntity(guide)) return 0;
            context.getSource().sendSuccess(() -> Component.translatable(
                !state.getBoolean("trial_complete") ? "message.shadowrp.guide_trial" :
                !state.getBoolean("oath_complete") ? "message.shadowrp.guide_oath" :
                state.getBoolean("nether_claimed") ? "message.shadowrp.nether_done" :
                state.getBoolean("relic_complete") ?
                    (state.getBoolean("nether_visited") ? "message.shadowrp.nether_ready" :
                        "message.shadowrp.nether_request") :
                state.getBoolean("rank_claimed") ? "message.shadowrp.relic_request_" + state.getString("faction") :
                state.getBoolean("scout_complete") ? "message.shadowrp.rank_ready" :
                state.getBoolean("supplies_complete") ? "message.shadowrp.scout_request" :
                "message.shadowrp.supplies_request"), false);
            return 1;
        }));

        // Find an existing Keeper without spawning another NPC or changing
        // quest state. Coordinates refer only to the player's current dimension.
        root.then(Commands.literal("locate").executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            String factionId = progress(player).getString("faction");
            if (!"shadow".equals(factionId) && !"light".equals(factionId)) {
                context.getSource().sendFailure(
                    Component.translatable("message.shadowrp.guide_requires_faction"));
                return 0;
            }
            Villager nearest = null;
            double nearestDistance = Double.POSITIVE_INFINITY;
            for (Villager villager : player.serverLevel().getEntitiesOfClass(
                    Villager.class, player.getBoundingBox().inflate(128.0),
                    candidate -> candidate.getTags().contains("shadowrp_guide") &&
                        ((!candidate.getTags().contains("shadowrp_guide_shadow") &&
                          !candidate.getTags().contains("shadowrp_guide_light")) ||
                         candidate.getTags().contains("shadowrp_guide_" + factionId)))) {
                double distance = villager.distanceToSqr(player);
                if (distance < nearestDistance) {
                    nearest = villager;
                    nearestDistance = distance;
                }
            }
            if (nearest == null) {
                context.getSource().sendFailure(
                    Component.translatable("message.shadowrp.guide_not_found"));
                return 0;
            }
            final Villager guide = nearest;
            context.getSource().sendSuccess(() -> Component.translatable(
                "message.shadowrp.guide_location",
                guide.blockPosition().getX(), guide.blockPosition().getY(),
                guide.blockPosition().getZ()), false);
            return 1;
        }));

        // A one-time chapter completion reward is persisted server-side.
        // It cannot be repeatedly claimed after relogging or dying.
        root.then(Commands.literal("oath").executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            CompoundTag state = progress(player);
            // Do not permanently lock a player to an empty faction if an
            // older or externally edited save has a completed trial.
            if (!state.getBoolean("started") ||
                (!"shadow".equals(state.getString("faction")) &&
                 !"light".equals(state.getString("faction")))) {
                context.getSource().sendFailure(
                    Component.translatable("message.shadowrp.guide_requires_faction"));
                return 0;
            }
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

        // Live quest journal: reads server-side progress and inventory, never
        // consumes resources or grants rewards. Works after reconnect/respawn.
        root.then(Commands.literal("journal").executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            CompoundTag state = progress(player);
            Component objective;
            if (!state.getBoolean("started")) {
                objective = Component.translatable("message.shadowrp.status_new");
            } else if (state.getString("faction").isEmpty()) {
                objective = Component.translatable("message.shadowrp.status_faction");
            } else if (state.getBoolean("trophy_complete")) {
                objective = Component.translatable("message.shadowrp.trophy_done");
            } else if (state.getBoolean("nether_claimed")) {
                boolean shadow = "shadow".equals(state.getString("faction"));
                objective = Component.translatable(
                    "message.shadowrp.journal_trophy_" + state.getString("faction"),
                    countInventoryItem(player, shadow ? Items.BLAZE_ROD : Items.QUARTZ));
            } else if (state.getBoolean("relic_complete")) {
                objective = Component.translatable(state.getBoolean("nether_visited")
                    ? "message.shadowrp.nether_ready" : "message.shadowrp.nether_request");
            } else if (state.getBoolean("rank_claimed")) {
                boolean shadow = "shadow".equals(state.getString("faction"));
                objective = Component.translatable(
                    "message.shadowrp.journal_relic_" + state.getString("faction"),
                    countInventoryItem(player, shadow ? Items.ENDER_PEARL : Items.GLOWSTONE_DUST));
            } else if (state.getBoolean("scout_complete")) {
                objective = Component.translatable("message.shadowrp.status_rank_ready",
                    state.getString("faction"));
            } else if (state.getBoolean("supplies_complete")) {
                objective = Component.translatable(
                    "message.shadowrp.journal_scout_" + state.getString("faction"),
                    state.getInt("scout_kills"));
            } else if (state.getBoolean("oath_complete")) {
                objective = Component.translatable("message.shadowrp.journal_supplies",
                    countInventoryItem(player, Items.WHEAT),
                    countInventoryItem(player, Items.OAK_LOG));
            } else if (state.getBoolean("trial_complete")) {
                objective = Component.translatable("message.shadowrp.status_done",
                    state.getString("faction"));
            } else {
                objective = Component.translatable("message.shadowrp.journal_trial",
                    state.getInt("trial_kills"));
            }
            context.getSource().sendSuccess(() -> objective, false);
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
            } else if (state.getBoolean("trophy_complete")) {
                message = Component.translatable("message.shadowrp.status_trophy_done", state.getString("faction"));
            } else if (state.getBoolean("nether_claimed")) {
                message = Component.translatable("message.shadowrp.status_trophy", state.getString("faction"));
            } else if (state.getBoolean("relic_complete")) {
                message = Component.translatable(state.getBoolean("nether_visited")
                    ? "message.shadowrp.nether_ready" : "message.shadowrp.nether_request");
            } else if (state.getBoolean("rank_claimed")) {
                message = Component.translatable("message.shadowrp.status_relic", state.getString("faction"));
            } else if (state.getBoolean("scout_complete")) {
                message = Component.translatable("message.shadowrp.status_rank_ready", state.getString("faction"));
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
        // Players cannot complete their own faction quests at the other
        // Garden's Keeper. Legacy unaligned Keepers remain usable.
        if ((guide.getTags().contains("shadowrp_guide_shadow") &&
                !"shadow".equals(state.getString("faction"))) ||
            (guide.getTags().contains("shadowrp_guide_light") &&
                !"light".equals(state.getString("faction")))) {
            player.sendSystemMessage(Component.translatable("message.shadowrp.other_faction_guide"));
            return;
        }
        if (!state.getBoolean("started") || state.getString("faction").isEmpty()) {
            message = "message.shadowrp.guide_requires_faction";
        } else if (!state.getBoolean("trial_complete")) {
            message = "message.shadowrp.guide_trial";
        } else if (!state.getBoolean("oath_complete")) {
            message = "message.shadowrp.guide_oath";
        } else if (state.getBoolean("trophy_complete")) {
            message = "message.shadowrp.trophy_done";
        } else if (state.getBoolean("nether_claimed")) {
            // Chapter three: a faction-specific Nether trophy, turned in
            // deliberately at the correct Keeper. Rewards cannot be farmed.
            boolean shadow = "shadow".equals(state.getString("faction"));
            net.minecraft.world.item.Item trophy = shadow ? Items.BLAZE_ROD : Items.QUARTZ;
            int required = shadow ? 2 : 8;
            if (countInventoryItem(player, trophy) < required) {
                message = "message.shadowrp.trophy_request_" + state.getString("faction");
            } else {
                removeInventoryItem(player, trophy, required);
                player.getInventory().setChanged();
                state.putBoolean("trophy_complete", true);
                save(player, state);
                player.giveExperiencePoints(250);
                ItemStack reward = new ItemStack(Items.DIAMOND, 2);
                if (!player.getInventory().add(reward)) player.drop(reward, false);
                message = "message.shadowrp.trophy_complete";
            }
        } else if (state.getBoolean("relic_complete")) {
            if (!state.getBoolean("nether_visited")) {
                message = "message.shadowrp.nether_request";
            } else {
                // Completing the expedition is deliberate: the player must
                // return to the Keeper. Claim once, persist before rewards.
                state.putBoolean("nether_claimed", true);
                save(player, state);
                player.giveExperiencePoints(200);
                ItemStack reward = new ItemStack(Items.EMERALD, 4);
                if (!player.getInventory().add(reward)) player.drop(reward, false);
                message = "message.shadowrp.nether_complete";
            }
        } else if (state.getBoolean("rank_claimed")) {
            // Chapter two begins only after the first rank has been claimed.
            // Each faction collects a different relic. Consume the exact
            // requirement and persist completion before granting a reward.
            boolean shadow = "shadow".equals(state.getString("faction"));
            net.minecraft.world.item.Item required =
                shadow ? Items.ENDER_PEARL : Items.GLOWSTONE_DUST;
            int requiredCount = shadow ? 2 : 8;
            if (countInventoryItem(player, required) < requiredCount) {
                message = "message.shadowrp.relic_request_" + state.getString("faction");
            } else {
                removeInventoryItem(player, required, requiredCount);
                player.getInventory().setChanged();
                state.putBoolean("relic_complete", true);
                save(player, state);
                player.giveExperiencePoints(150);
                ItemStack reward = new ItemStack(shadow ? Items.OBSIDIAN : Items.GOLD_INGOT, 2);
                if (!player.getInventory().add(reward)) player.drop(reward, false);
                message = "message.shadowrp.relic_complete_" + state.getString("faction");
            }
        } else if (state.getBoolean("scout_complete")) {
            // A final return to the Keeper completes the first faction
            // chapter. Persist before awarding so repeat clicks cannot farm.
            state.putBoolean("rank_claimed", true);
            save(player, state);
            player.giveExperiencePoints(125);
            ItemStack reward = new ItemStack(Items.DIAMOND, 1);
            if (!player.getInventory().add(reward)) player.drop(reward, false);
            message = "message.shadowrp.rank_complete";
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
    public void onDimensionChanged(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) ||
                !Level.NETHER.equals(event.getTo())) return;
        CompoundTag state = progress(player);
        // An earlier Nether visit cannot skip the faction relic quest.
        if (!state.getBoolean("relic_complete") ||
                state.getBoolean("nether_visited")) return;
        state.putBoolean("nether_visited", true);
        save(player, state);
        player.sendSystemMessage(Component.translatable("message.shadowrp.nether_discovered"));
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
