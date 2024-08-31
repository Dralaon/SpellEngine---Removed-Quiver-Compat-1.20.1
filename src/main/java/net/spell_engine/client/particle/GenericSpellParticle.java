package net.spell_engine.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.random.Random;
import net.spell_engine.client.util.Color;
import net.spell_power.api.SpellSchool;
import net.spell_power.api.SpellSchools;

public class GenericSpellParticle extends SpriteBillboardParticle  {
    private static final Random RANDOM = Random.create();
    private final SpriteProvider spriteProvider;

    GenericSpellParticle(ClientWorld world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, SpriteProvider spriteProvider) {
        super(world, x, y, z, 0.5 - RANDOM.nextDouble(), velocityY, 0.5 - RANDOM.nextDouble());
        this.velocityMultiplier = 0.96F;
        this.gravityStrength = -0.1F;
        this.ascending = true;
        this.spriteProvider = spriteProvider;
        this.velocityY *= 0.2;
        if (velocityX == 0.0 && velocityZ == 0.0) {
            this.velocityX *= 0.10000000149011612;
            this.velocityZ *= 0.10000000149011612;
        }

        this.scale *= 0.75F;
        this.maxAge = (int)(8.0 / (Math.random() * 0.8 + 0.2));
        this.collidesWithWorld = false;
        this.setSpriteForAge(spriteProvider);
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public int getBrightness(float tint) {
        return 255;
    }

    // MARK: Factories

    @Environment(EnvType.CLIENT)
    static class SpellFactory implements ParticleFactory<SimpleParticleType> {
        private final SpriteProvider spriteProvider;
        public final Color color;

        public SpellFactory(SpriteProvider spriteProvider, Color color) {
            this.spriteProvider = spriteProvider;
            this.color = color;
        }

        public Particle createParticle(SimpleParticleType SimpleParticleType, ClientWorld clientWorld, double d, double e, double f, double g, double h, double i) {
            var spellParticle = new GenericSpellParticle(clientWorld, d, e, f, g, h, i, this.spriteProvider);
            float j = clientWorld.random.nextFloat() * 0.5F + 0.35F;
            // 0xff66ff
            spellParticle.setColor(color.red() * j, color.green() * j, color.blue() * j);
//            spellParticle.alpha = 1F;
            return spellParticle;
        }
    }


    @Environment(EnvType.CLIENT)
    public static class ArcaneSpellFactory extends SpellFactory implements ParticleFactory<SimpleParticleType> {
        public ArcaneSpellFactory(SpriteProvider spriteProvider) {
            super(spriteProvider, Color.from(SpellSchools.ARCANE.color));
        }
    }

    @Environment(EnvType.CLIENT)
    public static class HolySpellFactory extends SpellFactory implements ParticleFactory<SimpleParticleType> {
        public HolySpellFactory(SpriteProvider spriteProvider) {
            super(spriteProvider, Color.from(0xffffcc));
        }
    }

    @Environment(EnvType.CLIENT)
    public static class ArcaneSparkFactory implements ParticleFactory<SimpleParticleType> {
        private final SpriteProvider spriteProvider;

        public ArcaneSparkFactory(SpriteProvider spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        public static Color color = Color.from(SpellSchools.ARCANE.color);

        public Particle createParticle(SimpleParticleType SimpleParticleType, ClientWorld clientWorld, double d, double e, double f, double g, double h, double i) {
            var spellParticle = new GenericSpellParticle(clientWorld, d, e, f, g, h, i, this.spriteProvider);
            float j = clientWorld.random.nextFloat() * 0.5F + 0.35F;
            // 0xff66ff
            spellParticle.setColor(color.red() * j, color.green() * j, color.blue() * j);
            spellParticle.alpha = 0.5F;
            return spellParticle;
        }
    }
}
