package com.veyrion.shadowrp;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Monster;
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

        root.then(Commands.literal("status").executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            CompoundTag state = progress(player);
            Component message;
            if (!state.getBoolean("started")) {
                message = Component.translatable("message.shadowrp.status_new");
            } else if (state.getString("faction").isEmpty()) {
                message = Component.translatable("message.shadowrp.status_faction");
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
        if (!state.getBoolean("started") || state.getString("faction").isEmpty()
                || state.getBoolean("trial_complete")) {
            return;
        }
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
