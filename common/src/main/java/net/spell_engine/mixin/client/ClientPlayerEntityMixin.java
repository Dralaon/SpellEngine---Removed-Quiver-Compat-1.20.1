package net.spell_engine.mixin.client;

import net.spell_engine.api.spell.Spell;
import net.spell_engine.client.CombatSpellsClient;
import net.spell_engine.internals.SpellCastAction;
import net.spell_engine.internals.SpellCasterClient;
import net.spell_engine.internals.SpellHelper;
import net.spell_engine.network.Packets;
import net.spell_engine.utils.TargetHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin implements SpellCasterClient {
    @Shadow @Final public ClientPlayNetworkHandler networkHandler;
    private List<Entity> targets = List.of();

    private ClientPlayerEntity player() {
        return (ClientPlayerEntity) ((Object) this);
    }

    private Entity firstTarget() {
        return targets.stream().findFirst().orElse(null);
    }

    public List<Entity> getCurrentTargets() {
        if (targets == null) {
            return List.of();
        }
        return targets;
    }

    public Entity getCurrentFirstTarget() {
        return firstTarget();
    }

    @Override
    public void castStart(Spell spell, ItemStack itemStack, int remainingUseTicks) {
        System.out.println("Spell casting - Start");
        var caster = player();
        var slot = findSlot(caster, itemStack);
        ClientPlayNetworking.send(
                Packets.SpellRequest.ID,
                new Packets.SpellRequest(SpellCastAction.START, slot, remainingUseTicks, new int[]{}).write());
    }

    @Override
    public void castTick(ItemStack itemStack, int remainingUseTicks) {
        var currentSpell = getCurrentSpell();
        if (currentSpell == null) {
            return;
        }

        updateTargets();
        var progress = SpellHelper.getCastProgress(player(), remainingUseTicks, currentSpell);
        if (SpellHelper.isChanneled(currentSpell)) {
            var action = (progress >= 1) ? SpellCastAction.RELEASE : SpellCastAction.CHANNEL;
            cast(currentSpell, action, itemStack, remainingUseTicks);
        } else {
            if (CombatSpellsClient.config.autoRelease
                    && SpellHelper.getCastProgress(player(), remainingUseTicks, currentSpell) >= 1) {
                cast(currentSpell, SpellCastAction.RELEASE, itemStack, remainingUseTicks);
            }
        }
    }

    @Override
    public void castRelease(ItemStack itemStack, int remainingUseTicks) {
        updateTargets();
        cast(getCurrentSpell(), SpellCastAction.RELEASE, itemStack, remainingUseTicks);
    }

    private void cast(Spell spell, SpellCastAction action, ItemStack itemStack, int remainingUseTicks) {
        if (spell == null) {
            return;
        }
        var caster = player();
        var progress = SpellHelper.getCastProgress(caster, remainingUseTicks, spell);
        var isChannelled = SpellHelper.isChanneled(spell);
        boolean shouldEndCasting = false;
        switch (action) {
            case CHANNEL -> {
                if (!isChannelled
                        || !SpellHelper.isChannelTickDue(spell, remainingUseTicks)) {
                    return;
                }
                shouldEndCasting = progress >= 1;
            }
            case RELEASE -> {
                if (!isChannelled && progress < 1) {
                    return;
                }
                shouldEndCasting = true;
            }
        }

        var slot = findSlot(caster, itemStack);
        var release = spell.on_release.target;
        int[] targetIDs = new int[]{};
        switch (release.type) {
            case PROJECTILE, CURSOR -> {
                var firstTarget = firstTarget();
                if (firstTarget != null) {
                    targetIDs = new int[]{ firstTarget.getId() };
                }
            }
            case AREA, BEAM -> {
                targetIDs = new int[targets.size()];
                int i = 0;
                for (var target : targets) {
                    targetIDs[i] = target.getId();
                    i += 1;
                }
            }
        }
        System.out.println("Sending spell cast packet: " + new Packets.SpellRequest(action, slot, remainingUseTicks, targetIDs));
        ClientPlayNetworking.send(
                Packets.SpellRequest.ID,
                new Packets.SpellRequest(action, slot, remainingUseTicks, targetIDs).write());

        if (shouldEndCasting) {
            player().clearActiveItem();
        }
    }

    private int findSlot(PlayerEntity player, ItemStack stack) {
        var inventory = player.getInventory().main;
        for(int i = 0; i < inventory.size(); ++i) {
            ItemStack itemStack = inventory.get(i);
            if (stack == itemStack) {
                return i;
            }
        }
        return -1;
    }

    private void updateTargets() {
        targets = findTargets(getCurrentSpell());
    }

    private List<Entity> findTargets(Spell currentSpell) {
        var caster = player();
        List<Entity> targets = List.of();
        if (currentSpell == null) {
            return targets;
        }
        switch (currentSpell.on_release.target.type) {
            case AREA -> {
                targets = TargetHelper.targetsFromArea(caster, currentSpell.range, currentSpell.on_release.target.area);
            }
            case BEAM -> {
                targets = TargetHelper.targetsFromRaycast(caster, currentSpell.range);
            }
            case CURSOR, PROJECTILE -> {
                var target = TargetHelper.targetFromRaycast(caster, currentSpell.range);
                if (target != null) {
                    targets = List.of(target);
                } else {
                    targets = List.of();
                }
                var cursor = currentSpell.on_release.target.cursor;
                if (cursor != null) {
                    var firstTarget = firstTarget();
                    if (firstTarget == null && cursor.use_caster_as_fallback) {
                        targets = List.of(caster);
                    }
                }
            }
        }
        return targets;
    }

//    @Inject(method = "clearActiveItem", at = @At("TAIL"))
//    private void clearCurrentSpell(CallbackInfo ci) {
//        System.out.println("Cast cancel");
//        currentSpell = null;
//    }

    @Inject(method = "tick", at = @At("TAIL"))
    public void tick_TAIL(CallbackInfo ci) {
        var player = player();
        if (!player.isUsingItem()) {
            targets = List.of();
        }
        if (isBeaming()) {
            networkHandler.sendPacket(new PlayerMoveC2SPacket.Full(
                    player.getX(), player.getY(), player.getZ(),
                    player.getYaw(), player.getPitch(),
                    player.isOnGround())
            );
        }
    }
}