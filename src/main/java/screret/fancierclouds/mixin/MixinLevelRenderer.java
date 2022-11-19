package screret.fancierclouds.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.math.Matrix4f;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {

    private static final ResourceLocation CLOUDS_LOCATION = new ResourceLocation("textures/environment/clouds.png");
    private static final int CLOUD_DENSITY_STEPS = 4;

    @Shadow private ClientLevel level;
    @Shadow private int ticks;
    @Shadow private int prevCloudX = Integer.MIN_VALUE, prevCloudY = Integer.MIN_VALUE, prevCloudZ = Integer.MIN_VALUE;
    @Shadow @Final private Minecraft minecraft;
    @Shadow private Vec3 prevCloudColor = Vec3.ZERO;
    @Shadow private CloudStatus prevCloudsType;
    @Shadow private boolean generateClouds;
    @Shadow private VertexBuffer cloudBuffer;


    @Invoker
    BufferBuilder.RenderedBuffer callBuildClouds(BufferBuilder pBuilder, double pX, double pY, double pZ, Vec3 pCloudColor){
        throw new IllegalStateException("Failed to mixin buildClouds()");
    }

    @Inject(method = "Lnet/minecraft/client/renderer/LevelRenderer;renderClouds(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/math/Matrix4f;FDDD)V", at = @At("HEAD"))
    public void fancierClouds$renderClouds(PoseStack pPoseStack, Matrix4f pProjectionMatrix, float pPartialTick, double pCamX, double pCamY, double pCamZ, CallbackInfo ci) {
        if (level.effects().renderClouds(level, ticks, pPartialTick, pPoseStack, pCamX, pCamY, pCamZ, pProjectionMatrix))
            return;
        float cloudHeight = this.level.effects().getCloudHeight();
        if (!Float.isNaN(cloudHeight)) {
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.enableDepthTest();
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            RenderSystem.depthMask(true);
            float scaleXZ = 12.0F;
            float scaleY = 4.0F;
            double time = ((float)this.ticks + pPartialTick) * 0.03F;
            double renderX = (pCamX + time) / scaleXZ;
            double renderY = cloudHeight - (float)pCamY + 0.33F;
            double renderZ = pCamZ / scaleXZ + (double)0.33F;
            renderX -= Mth.floor(renderX / 2048.0D) * 2048;
            renderZ -= Mth.floor(renderZ / 2048.0D) * 2048;
            float realX = (float)(renderX - (double)Mth.floor(renderX));
            float realY = (float)(renderY / scaleY - (double)Mth.floor(renderY / scaleY)) * 4.0F;
            float realZ = (float)(renderZ - (double)Mth.floor(renderZ));
            Vec3 cloudColor = this.level.getCloudColor(pPartialTick);
            int x = (int)Math.floor(renderX);
            int y = (int)Math.floor(renderY / scaleY);
            int z = (int)Math.floor(renderZ);
            if (x != this.prevCloudX || y != this.prevCloudY || z != this.prevCloudZ || this.minecraft.options.getCloudsType() != this.prevCloudsType || this.prevCloudColor.distanceToSqr(cloudColor) > 2.0E-4D) {
                this.prevCloudX = x;
                this.prevCloudY = y;
                this.prevCloudZ = z;
                this.prevCloudColor = cloudColor;
                this.prevCloudsType = this.minecraft.options.getCloudsType();
                this.generateClouds = true;
            }

            if (this.generateClouds) {
                this.generateClouds = false;
                BufferBuilder bufferbuilder = Tesselator.getInstance().getBuilder();
                if (this.cloudBuffer != null) {
                    this.cloudBuffer.close();
                }

                this.cloudBuffer = new VertexBuffer();
                BufferBuilder.RenderedBuffer buffer = callBuildClouds(bufferbuilder, renderX, renderY, renderZ, cloudColor);
                this.cloudBuffer.bind();
                this.cloudBuffer.upload(buffer);
                VertexBuffer.unbind();
            }

            RenderSystem.setShader(GameRenderer::getPositionTexColorNormalShader);
            RenderSystem.setShaderTexture(0, CLOUDS_LOCATION);
            FogRenderer.levelFogColor();
            pPoseStack.pushPose();
            pPoseStack.scale(scaleXZ, 1.0F, scaleXZ);
            pPoseStack.translate(-realX, realY, -realZ);
            if (this.cloudBuffer != null) {
                this.cloudBuffer.bind();
                int cloudType = this.prevCloudsType == CloudStatus.FANCY ? 0 : 1;

                for(int i = cloudType; i < 2; ++i) {
                    if (i == 0) {
                        RenderSystem.colorMask(false, false, false, false);
                    } else {
                        RenderSystem.colorMask(true, true, true, true);
                    }

                    ShaderInstance shader = RenderSystem.getShader();
                    var cloudBounds = new AABB(realX, realY, realZ, scaleXZ, scaleY, scaleXZ);
                    if(this.level.getBlockStates(cloudBounds).allMatch(BlockState::isAir) && testBiomes(cloudBounds)){
                        var height = this.level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, Mth.floor(realX), Mth.floor(realZ));
                        var heightDivMaxHeight = height / this.level.getHeight();
                        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, heightDivMaxHeight);
                        this.cloudBuffer.drawWithShader(pPoseStack.last().pose(), pProjectionMatrix, shader);
                        for(double c = 1; c <= CLOUD_DENSITY_STEPS; ++c){
                            var densityAtHeight = height / c;
                            if(densityAtHeight > 8){
                                renderExtraClouds(pPoseStack, pProjectionMatrix, shader, scaleXZ, Direction.SOUTH);
                            }
                            if(densityAtHeight > 16){
                                renderExtraClouds(pPoseStack, pProjectionMatrix, shader, scaleXZ, Direction.NORTH);
                            }
                            if(densityAtHeight > 32){
                                renderExtraClouds(pPoseStack, pProjectionMatrix, shader, scaleXZ, Direction.WEST);
                            }
                            if(densityAtHeight > 64){
                                renderExtraClouds(pPoseStack, pProjectionMatrix, shader, scaleXZ, Direction.EAST);
                            }
                        }
                    }
                }

                VertexBuffer.unbind();
            }

            pPoseStack.popPose();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
    }

    @Unique
    private boolean testBiomes(AABB cloudPos){
        for(double x = cloudPos.minX; x < cloudPos.maxX; ++x){
            for (double y = cloudPos.minY; y < cloudPos.maxY; ++y) {
                for (double z = cloudPos.minZ; z < cloudPos.maxZ; ++z) {
                    if(this.level.getBiome(new BlockPos(x, y, z)).is(Tags.Biomes.IS_DRY)){
                        return false;
                    }
                }
            }
        }
        return true;
    }

    @Unique
    private void renderExtraClouds(PoseStack poseStack, Matrix4f projectionMatrix, ShaderInstance shader, float scaleXZ, Direction direction){
        poseStack.translate(scaleXZ * direction.getStepX(), 0, scaleXZ * direction.getStepZ());
        this.cloudBuffer.drawWithShader(poseStack.last().pose(), projectionMatrix, shader);
        poseStack.translate(-scaleXZ * direction.getStepX(), 0, -scaleXZ * direction.getStepZ());
    }
}
