package com.redsmods.common.client.model;

// Made with Blockbench 4.12.5
// Exported for Minecraft version 1.17 or later with Mojang mappings
// Paste this class into your mod and generate all required imports

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.redsmods.RedsBosses;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;

public class RadianceModel<T extends com.redsmods.common.entity.Radiance> extends HierarchicalModel<T> {
	// This layer location should be baked with EntityRendererProvider.Context in the entity renderer and passed into this model's constructor
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(RedsBosses.MODID, "radiance"), "main");
	private final ModelPart root;
	private final ModelPart legRight;
	private final ModelPart legLeft;
	private final ModelPart group;
	private final ModelPart wingRight;
	private final ModelPart wingLeft;
	private final ModelPart bb_main;

	public RadianceModel(ModelPart root) {
		this.root = root;
		this.legRight = root.getChild("legRight");
		this.legLeft = root.getChild("legLeft");
		this.group = root.getChild("group");
		this.wingRight = root.getChild("wingRight");
		this.wingLeft = root.getChild("wingLeft");
		this.bb_main = root.getChild("bb_main");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition legRight = partdefinition.addOrReplaceChild("legRight", CubeListBuilder.create().texOffs(80, 41).addBox(-7.0F, 4.0F, -7.0F, 5.0F, 11.0F, 14.0F, new CubeDeformation(0.0F))
		.texOffs(0, 58).addBox(-8.0F, -7.0F, -7.0F, 6.0F, 11.0F, 14.0F, new CubeDeformation(0.0F)), PartPose.offset(1.0F, 5.0F, 0.0F));

		PartDefinition legLeft = partdefinition.addOrReplaceChild("legLeft", CubeListBuilder.create(), PartPose.offset(-1.0F, 5.0F, 0.0F));

		PartDefinition cube_r1 = legLeft.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(40, 58).addBox(-8.0F, 4.0F, -7.0F, 6.0F, 11.0F, 14.0F, new CubeDeformation(0.0F))
		.texOffs(80, 66).addBox(-7.0F, 15.0F, -7.0F, 5.0F, 11.0F, 14.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -11.0F, 0.0F, 0.0F, 3.1416F, 0.0F));

		PartDefinition group = partdefinition.addOrReplaceChild("group", CubeListBuilder.create().texOffs(90, 97).addBox(-2.0F, -15.0F, -4.0F, 4.0F, 15.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -27.0F, 0.0F));

		PartDefinition cube_r2 = group.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(28, 103).addBox(-2.0F, -8.0F, -5.0F, 4.0F, 8.0F, 10.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.3491F));

		PartDefinition cube_r3 = group.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(0, 101).addBox(-2.0F, -8.0F, -5.0F, 4.0F, 8.0F, 10.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.3491F));

		PartDefinition wingRight = partdefinition.addOrReplaceChild("wingRight", CubeListBuilder.create().texOffs(0, 52).addBox(-28.2552F, -7.1867F, 0.9548F, 28.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(-6.7448F, -15.8133F, 0.0452F));

		PartDefinition cube_r4 = wingRight.addOrReplaceChild("cube_r4", CubeListBuilder.create().texOffs(0, 89).addBox(5.0F, -2.0F, -1.0F, 22.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-21.2552F, 14.8133F, 1.9548F, -0.0895F, 0.0917F, -0.6007F));

		PartDefinition cube_r5 = wingRight.addOrReplaceChild("cube_r5", CubeListBuilder.create().texOffs(0, 83).addBox(5.0F, -2.0F, -1.0F, 22.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-26.2552F, 2.8133F, -0.0452F, -0.0148F, 0.0249F, -0.2164F));

		PartDefinition cube_r6 = wingRight.addOrReplaceChild("cube_r6", CubeListBuilder.create().texOffs(96, 91).addBox(8.0F, -2.0F, -1.0F, 19.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-19.2552F, -24.1867F, 0.9548F, 0.0F, 0.0F, 0.7418F));

		PartDefinition cube_r7 = wingRight.addOrReplaceChild("cube_r7", CubeListBuilder.create().texOffs(56, 23).addBox(-1.0F, -2.0F, -1.0F, 28.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-26.2552F, -13.1867F, -0.0452F, 0.0F, 0.0F, 0.2182F));

		PartDefinition wingLeft = partdefinition.addOrReplaceChild("wingLeft", CubeListBuilder.create().texOffs(56, 29).addBox(-28.2552F, -7.1867F, 0.9548F, 28.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.2552F, -15.8133F, 0.0452F, 0.0F, 3.1416F, 0.0F));

		PartDefinition cube_r8 = wingLeft.addOrReplaceChild("cube_r8", CubeListBuilder.create().texOffs(0, 95).addBox(5.0F, -2.0F, -1.0F, 22.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-21.2552F, 14.8133F, 1.9548F, -0.0895F, 0.0917F, -0.6007F));

		PartDefinition cube_r9 = wingLeft.addOrReplaceChild("cube_r9", CubeListBuilder.create().texOffs(48, 91).addBox(5.0F, -2.0F, -1.0F, 22.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-26.2552F, 2.8133F, -0.0452F, -0.0148F, 0.0249F, -0.2164F));

		PartDefinition cube_r10 = wingLeft.addOrReplaceChild("cube_r10", CubeListBuilder.create().texOffs(48, 97).addBox(8.0F, -2.0F, -1.0F, 19.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-19.2552F, -24.1867F, 0.9548F, 0.0F, 0.0F, 0.7418F));

		PartDefinition cube_r11 = wingLeft.addOrReplaceChild("cube_r11", CubeListBuilder.create().texOffs(56, 35).addBox(-1.0F, -2.0F, -1.0F, 28.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-26.2552F, -13.1867F, -0.0452F, 0.0F, 0.0F, 0.2182F));

		PartDefinition bb_main = partdefinition.addOrReplaceChild("bb_main", CubeListBuilder.create().texOffs(0, 0).addBox(-8.0F, -33.0F, -8.0F, 16.0F, 7.0F, 16.0F, new CubeDeformation(0.0F))
		.texOffs(0, 23).addBox(-7.0F, -47.0F, -7.0F, 14.0F, 15.0F, 14.0F, new CubeDeformation(0.0F))
		.texOffs(64, 0).addBox(-5.0F, -52.0F, -6.0F, 10.0F, 8.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 24.0F, 0.0F));

		return LayerDefinition.create(meshdefinition, 256, 256);
	}


	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
		legRight.render(poseStack, buffer, packedLight, packedOverlay, color);
		legLeft.render(poseStack, buffer, packedLight, packedOverlay, color);
		group.render(poseStack, buffer, packedLight, packedOverlay, color);
		wingRight.render(poseStack, buffer, packedLight, packedOverlay, color);
		wingLeft.render(poseStack, buffer, packedLight, packedOverlay, color);
		bb_main.render(poseStack, buffer, packedLight, packedOverlay, color);
	}

	@Override
	public ModelPart root() {
		return this.root;
	}

	@Override
	public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {

	}
}