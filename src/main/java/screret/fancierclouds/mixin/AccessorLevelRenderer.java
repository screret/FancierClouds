package screret.fancierclouds.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LevelRenderer.class)
public interface AccessorLevelRenderer {

    @Invoker
    BufferBuilder.RenderedBuffer callBuildClouds(BufferBuilder p_234262_, double p_234263_, double p_234264_, double p_234265_, Vec3 p_234266_);
}
