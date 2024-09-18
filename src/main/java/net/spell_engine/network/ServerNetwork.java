package net.spell_engine.network;

import com.google.common.collect.Iterables;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.world.ServerWorld;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.internals.SpellCastSyncHelper;
import net.spell_engine.internals.SpellHelper;
import net.spell_engine.internals.SpellRegistry;

import java.util.ArrayList;
import java.util.List;

public class ServerNetwork {
    private static PacketByteBuf configSerialized = PacketByteBufs.create();

    public static void initializeHandlers() {
        configSerialized = Packets.ConfigSync.write(SpellEngineMod.config);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            sender.sendPacket(Packets.SpellRegistrySync.ID, SpellRegistry.encoded);
            sender.sendPacket(Packets.ConfigSync.ID, configSerialized);
        });

        ServerPlayNetworking.registerGlobalReceiver(Packets.SpellCastSync.ID, (server, player, handler, buf, responseSender) -> {
            ServerWorld world = Iterables.tryFind(server.getWorlds(), (element) -> element == player.getWorld())
                    .orNull();
            if (world == null || world.isClient) {
                return;
            }
            var packet = Packets.SpellCastSync.read(buf);
            world.getServer().executeSync(() -> {
                if (packet.spellId() == null) {
                    SpellCastSyncHelper.clearCasting(player);
                } else {
                    SpellHelper.startCasting(player, packet.spellId(), packet.speed(), packet.length());
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(Packets.SpellRequest.ID, (server, player, handler, buf, responseSender) -> {
            ServerWorld world = Iterables.tryFind(server.getWorlds(), (element) -> element == player.getWorld())
                    .orNull();
            if (world == null || world.isClient) {
                return;
            }
            var packet = Packets.SpellRequest.read(buf);
            world.getServer().executeSync(() -> {
                List<Entity> targets = new ArrayList<>();
                for (var targetId: packet.targets()) {
                    // var entity = world.getEntityById(targetId);
                    var entity = world.getDragonPart(targetId); // Retrieves `getEntityById` + dragon parts :)
                    if (entity != null) {
                        targets.add(entity);
                    } else {
                        System.err.println("Spell Engine: Trying to perform spell " + packet.spellId().toString() + " Entity not found: " + targetId);
                    }
                }
                SpellHelper.performSpell(world, player, packet.spellId(), targets, packet.action(), packet.progress());
            });
        });
    }
}
