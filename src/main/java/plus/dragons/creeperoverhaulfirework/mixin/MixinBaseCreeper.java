package plus.dragons.creeperoverhaulfirework.mixin;

import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import love.marblegate.creeperfirework.misc.Configuration;
import love.marblegate.creeperfirework.misc.NetworkUtil;
import love.marblegate.creeperfirework.mixin.ExplosionMethodInvoker;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.enchantment.ProtectionEnchantment;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import net.minecraft.world.explosion.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib3.core.IAnimatable;
import tech.thatgravyboat.creeperoverhaul.common.entity.base.BaseCreeper;
import tech.thatgravyboat.creeperoverhaul.common.utils.PlatformUtils;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

@Mixin(BaseCreeper.class)
public abstract class MixinBaseCreeper extends CreeperEntity implements IAnimatable {

    public MixinBaseCreeper(EntityType<? extends CreeperEntity> entityType, World world) {
        super(entityType, world);
    }

    @Inject(method = "explode", at = @At("HEAD"), cancellable = true, remap = false)
    private void injected(CallbackInfo ci) {
        var self = ((BaseCreeper) (Object) this);
        if (Configuration.getRealTimeConfig().ACTIVE_EXPLOSION_TO_FIREWORK && new Random(self.getUuid().getLeastSignificantBits()).nextDouble() < Configuration.getRealTimeConfig().TURNING_PROBABILITY) {
            if (!self.getWorld().isClient()) {
                Explosion.DestructionType destructionType = PlatformUtils.getInteractionForCreeper(self);
                NetworkUtil.notifyClient((ServerWorld) self.getWorld(), self.getBlockPos(), false);
                if (Configuration.getRealTimeConfig().HURT_CREATURE)
                    simulateExplodeHurtMob();
                if (Configuration.getRealTimeConfig().DESTROY_BLOCK && self.getWorld().getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING))
                    simulateExplodeDestroyBlock(destructionType);
                spawnPotionCloud();
            }
            self.discard();
            ci.cancel();
        }

    }

    @Override
    public void onDeath(DamageSource damageSource) {
        var self = ((BaseCreeper) (Object) this);
        super.onDeath(damageSource);
        if (Configuration.getRealTimeConfig().DEATH_TO_FIREWORK && new Random(self.getUuid().getLeastSignificantBits()).nextDouble() < Configuration.getRealTimeConfig().DEATH_EXPLOSION_TURNING_PROBABILITY) {
            if (!self.getWorld().isClient()) {
                Explosion.DestructionType destructionType = PlatformUtils.getInteractionForCreeper(self);
                NetworkUtil.notifyClient((ServerWorld) self.getWorld(), self.getBlockPos(), false);
                if (Configuration.getRealTimeConfig().DEATH_EXPLOSION_HURT_CREATURE)
                    simulateExplodeHurtMob();
                if (Configuration.getRealTimeConfig().DEATH_EXPLOSION_DESTROY_BLOCK && self.getWorld().getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING))
                    simulateExplodeDestroyBlock(destructionType);
                spawnPotionCloud();
            }
        }
    }

    private void simulateExplodeDestroyBlock(Explosion.DestructionType destructionType) {
        var self = ((BaseCreeper) (Object) this);
        self.getWorld().emitGameEvent(self, GameEvent.EXPLODE, self.getBlockPos());
        Set<BlockPos> explosionRange = Sets.newHashSet();
        BlockPos groundZero = self.getBlockPos();
        for (int j = 0; j < 16; ++j) {
            for (int k = 0; k < 16; ++k) {
                for (int l = 0; l < 16; ++l) {
                    if (j == 0 || j == 15 || k == 0 || k == 15 || l == 0 || l == 15) {
                        double d = (float) j / 15.0F * 2.0F - 1.0F;
                        double e = (float) k / 15.0F * 2.0F - 1.0F;
                        double f = (float) l / 15.0F * 2.0F - 1.0F;
                        double g = Math.sqrt(d * d + e * e + f * f);
                        d /= g;
                        e /= g;
                        f /= g;
                        float h = getExplosionPower() * (0.7F + self.getWorld().random.nextFloat() * 0.6F);
                        double m = groundZero.getX();
                        double n = groundZero.getY();
                        double o = groundZero.getZ();
                        for (; h > 0.0F; h -= 0.22500001F) {
                            BlockPos blockPos = new BlockPos(m, n, o);
                            BlockState blockState = self.getWorld().getBlockState(blockPos);
                            FluidState fluidState = self.getWorld().getFluidState(blockPos);
                            if (!self.getWorld().isInBuildLimit(blockPos)) {
                                break;
                            }

                            Optional<Float> optional = blockState.isAir() && fluidState.isEmpty() ? Optional.empty() : Optional.of(Math.max(blockState.getBlock().getBlastResistance(), fluidState.getBlastResistance()));
                            if (optional.isPresent()) {
                                h -= (optional.get() + 0.3F) * 0.3F;
                            }

                            if (h > 0.0F) {
                                explosionRange.add(blockPos);
                            }

                            m += d * 0.30000001192092896D;
                            n += e * 0.30000001192092896D;
                            o += f * 0.30000001192092896D;
                        }
                    }
                }
            }
        }

        if (destructionType != Explosion.DestructionType.NONE) {
            ObjectArrayList<Pair<ItemStack, BlockPos>> blockDropList = new ObjectArrayList<>();

            /// I really do not want to create an explosion instance here. But there is a method below needs it.
            Explosion simulateExplosionForParameter = new Explosion(self.getWorld(), null, null, null,
                    self.getBlockX(), self.getBlockY(), self.getBlockZ(), getExplosionPower(), false, Explosion.DestructionType.DESTROY);

            for (BlockPos affectedPos : explosionRange) {
                BlockState blockStateOfAffected = self.getWorld().getBlockState(affectedPos);
                Block block = blockStateOfAffected.getBlock();
                if (!blockStateOfAffected.isAir()) {
                    BlockPos blockPos2 = affectedPos.toImmutable();
                    self.getWorld().getProfiler().push("explosion_blocks");

                    BlockEntity blockEntity = blockStateOfAffected.hasBlockEntity() ? self.getWorld().getBlockEntity(affectedPos) : null;
                    LootContext.Builder builder = (new LootContext.Builder((ServerWorld) self.getWorld())).random(self.getWorld().random).parameter(LootContextParameters.ORIGIN, Vec3d.ofCenter(affectedPos)).parameter(LootContextParameters.TOOL, ItemStack.EMPTY).optionalParameter(LootContextParameters.BLOCK_ENTITY, blockEntity).optionalParameter(LootContextParameters.THIS_ENTITY, self);
                    builder.parameter(LootContextParameters.EXPLOSION_RADIUS, getExplosionPower());

                    blockStateOfAffected.getDroppedStacks(builder).forEach((stack) -> {
                        ExplosionMethodInvoker.invokeTryMergeStack(blockDropList, stack, blockPos2);
                    });

                    self.getWorld().setBlockState(affectedPos, Blocks.AIR.getDefaultState(), 3);

                    // yes here is what I'm talking. This part cannot be deleted.
                    block.onDestroyedByExplosion(self.getWorld(), affectedPos, simulateExplosionForParameter);
                    self.getWorld().getProfiler().pop();
                }
            }

            for (Pair<ItemStack, BlockPos> itemStackBlockPosPair : blockDropList) {
                Block.dropStack(self.getWorld(), itemStackBlockPosPair.getSecond(), itemStackBlockPosPair.getFirst());
            }
        }

        // Creeper Overhaul Part
        if (!self.type.replacer().isEmpty()) {
            Set<Map.Entry<Predicate<BlockState>, Function<net.minecraft.util.math.random.Random, BlockState>>> entries = self.type.replacer().entrySet();
            explosionRange.stream().map(BlockPos::down).forEach((pos) -> {
                BlockState state = this.world.getBlockState(pos);
                Iterator var4 = entries.iterator();

                while (var4.hasNext()) {
                    Map.Entry<Predicate<BlockState>, Function<net.minecraft.util.math.random.Random, BlockState>> entry = (Map.Entry) var4.next();
                    if (((Predicate) entry.getKey()).test(state)) {
                        BlockState newState = (BlockState) ((Function) entry.getValue()).apply(this.random);
                        if (newState != null) {
                            this.world.setBlockState(pos, newState, 3);
                            break;
                        }
                    }
                }

            });
        }
    }

    private void simulateExplodeHurtMob() {
        var self = ((BaseCreeper) (Object) this);
        Vec3d groundZero = self.getPos();
        Box box = new Box(self.getBlockPos()).expand(getExplosionPower());
        List<LivingEntity> victims = self.getWorld().getNonSpectatingEntities(LivingEntity.class, box);
        for (LivingEntity victim : victims) {
            if (!victim.isImmuneToExplosion()) {
                float j = getExplosionPower() * 2.0F;
                double h = Math.sqrt(victim.squaredDistanceTo(groundZero)) / (double) j;
                if (h <= 1.0D) {
                    double s = victim.getX() - groundZero.x;
                    double t = victim.getEyeY() - groundZero.y;
                    double u = victim.getZ() - groundZero.z;
                    double blockPos = Math.sqrt(s * s + t * t + u * u);
                    if (blockPos != 0.0D) {
                        s /= blockPos;
                        t /= blockPos;
                        u /= blockPos;
                        double fluidState = Explosion.getExposure(groundZero, victim);
                        double v = (1.0D - h) * fluidState;
                        victim.damage(DamageSource.explosion(self), (float) ((int) ((v * v + v) / 2.0D * 7.0D * (double) j + 1.0D)));
                        double w = ProtectionEnchantment.transformExplosionKnockback((LivingEntity) victim, v);

                        victim.setVelocity(victim.getVelocity().add(s * w, t * w, u * w));
                    }
                }
            }
        }

        // Creeper Overhaul Part
        if (!self.type.inflictingPotions().isEmpty()) {
            var players = victims.stream().filter(livingEntity -> livingEntity instanceof PlayerEntity).toList();
            players.forEach((player) -> {
                Collection<StatusEffectInstance> inflictingPotions = self.type.inflictingPotions().stream().map(StatusEffectInstance::new).toList();
                inflictingPotions.forEach(player::addStatusEffect);
            });
        }
    }

    private void spawnPotionCloud() {
        var self = ((BaseCreeper) (Object) this);
        Stream<StatusEffectInstance> potions = Stream.concat(this.getStatusEffects().stream().map(StatusEffectInstance::new), self.type.potionsWhenDead().stream().map(StatusEffectInstance::new));
        ((SummonCloudWithEffectsMethodInvoker) self).invokeSummonCloudWithEffects(potions.toList());
    }

    private float getExplosionPower() {
        return 3.0F * (((BaseCreeper) (Object) this).shouldRenderOverlay() ? 2.0F : 1.0F);
    }
}
