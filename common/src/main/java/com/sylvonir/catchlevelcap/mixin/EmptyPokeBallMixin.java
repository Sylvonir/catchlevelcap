package com.sylvonir.catchlevelcap.mixin;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.BattleCaptureAction;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.render.ModelLayer;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.PokemonModelRepository;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.net.messages.client.battle.BattleCaptureEndPacket;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity;
import com.cobblemon.mod.common.util.PlayerExtensionsKt;
import com.cobblemon.mod.common.util.WorldExtensionsKt;

import java.util.Set;

import static com.sylvonir.catchlevelcap.CatchLevelCap.*;

@Mixin(EmptyPokeBallEntity.class)
public abstract class EmptyPokeBallMixin {

    @Shadow
    private PokemonEntity capturingPokemon;

    @Shadow protected abstract void drop();

    @Inject(method = "onEntityHit",
            at = @At(
                    value = "INVOKE",
                    //target = "Lcom/cobblemon/mod/common/entity/EntityProperty;set(Ljava/lang/Object;)V",
                    //target = "Lnet/minecraft/entity/data/DataTracker;set(Lnet/minecraft/entity/data/TrackedData<T>;T;)V",
                    target = "Lcom/cobblemon/mod/common/entity/pokeball/EmptyPokeBallEntity;attemptCatch(Lcom/cobblemon/mod/common/entity/pokemon/PokemonEntity;)V",
                    //target = "Lcom/cobblemon/mod/common/entity/pokeball/EmptyPokeBallEntity;getOwner()Lnet/minecraft/entity/Entity;",
                    shift = At.Shift.BEFORE,
                    ordinal = 0
            ),
            cancellable = true)
    private void applyLevelCap(EntityHitResult hitResult, CallbackInfo ci) {
        EmptyPokeBallEntity self = (EmptyPokeBallEntity) (Object) this;

        if (self.getOwner() instanceof ServerPlayerEntity player) {

            PokemonModelRepository instance = PokemonModelRepository.INSTANCE;
            Set<String> aspects = capturingPokemon.getPokemon().getAspects();
            Identifier identifier = capturingPokemon.getPokemon().getSpecies().resourceIdentifier;
            player.sendMessage(Text.literal(
                instance.getTextureNoSubstitute(identifier, aspects, 0F).toString()));
            player.sendMessage(Text.literal(
                    instance.getPoser(identifier, aspects).toString()));

            player.sendMessage(Text.literal(
                instance.getVariations().get(identifier).getResolvedTexture(aspects, 0F).toString()));

            for(String s : aspects) {
                player.sendMessage(Text.literal(s));
            }

            Iterable<ModelLayer> layers = instance.getLayers(identifier, aspects);

            for(ModelLayer layer : layers) {
                player.sendMessage(Text.literal(layer.getTexture().invoke(0F).toString()));
            }

            if (!player.isCreative() && !self.getPokeBall().getCatchRateModifier().isGuaranteed() && !capturingPokemon.getPokemon().getShiny()) {

                int playerLevel = catchlevelcap$getHighestPartyLevel(player);
                int targetLevel = capturingPokemon.getPokemon().getLevel();

                GameRules gamerules = self.getWorld().getGameRules();
                int levelCap = gamerules.getInt(LEVEL_CAP);
                int externalLevelCap = gamerules.getInt(EXTERNAL_LEVEL_CAP);
                boolean showMessages = gamerules.getBoolean(SHOW_LEVEL_CAP_MESSAGES);


                if (!capturingPokemon.isBattling()) {
                    if(targetLevel > playerLevel + externalLevelCap) {
                        if (showMessages) {
                            if (externalLevelCap < levelCap && targetLevel < playerLevel + levelCap) {
                                player.sendMessage(Text.translatableWithFallback("catchlevelcap.fail_message_external", "The ball bounced off... try battling first?").formatted(Formatting.RED), true);
                            } else {
                                player.sendMessage(Text.translatableWithFallback("catchlevelcap.fail_message", "It seems too strong to be caught...").formatted(Formatting.RED), true);
                            }

                        }

                        WorldExtensionsKt.playSoundServer(self.getWorld(), self.getPos(), SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.NEUTRAL, 0.8F, 1F);
                        drop();
                        ci.cancel();
                    }
                } else if (targetLevel > playerLevel + levelCap) {
                    PokemonBattle battle = PlayerExtensionsKt.getBattleState(player).component1();

                    ActiveBattlePokemon hitBattlePokemon = null;
                    if(battle != null) {
                        for (ActiveBattlePokemon battlePokemon : battle.getActor(player).getSide().getOppositeSide().getActivePokemon()) {
                            if (battlePokemon.getBattlePokemon().getEffectedPokemon().getEntity() == capturingPokemon) {
                                hitBattlePokemon = battlePokemon;
                            }
                        }
                    }
                    if(hitBattlePokemon != null) {
                        battle.broadcastChatMessage(Text.translatableWithFallback("catchlevelcap.fail_message_battle", "The ball had no effect...").formatted(Formatting.RED));
                        battle.sendUpdate(new BattleCaptureEndPacket(hitBattlePokemon.getPNX(), false));

                        BattleCaptureAction captureAction = null;
                        for(BattleCaptureAction action : battle.getCaptureActions()) {
                            if(action.getPokeBallEntity() == self) captureAction = action;
                        }
                        if(captureAction != null) {
                            battle.finishCaptureAction(captureAction);
                        }
                    }

                    if (showMessages) {
                        player.sendMessage(Text.translatableWithFallback("catchlevelcap.fail_message", "It seems too strong to be caught...").formatted(Formatting.RED), true);
                    }

                    WorldExtensionsKt.playSoundServer(self.getWorld(), self.getPos(), SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.NEUTRAL, 0.8F, 1F);
                    drop();
                    ci.cancel();
                }
            }
        }
    }

    @Unique
    private int catchlevelcap$getHighestPartyLevel(ServerPlayerEntity player) {
        PlayerPartyStore party = PlayerExtensionsKt.party(player);

        int max = 10;
        for(Pokemon pokemon : party) {
            int level = pokemon.getLevel();
            if(level > max) {
                max = level;
            }
        }
        return max;
    }
}