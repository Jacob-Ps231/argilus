package re.jerome.argilus.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartNames;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

// Geometry written by hand rather than exported, the way vanilla does it:
// IronGolemModel.createBodyLayer is nothing but a list of boxes in code.
//
// Sixteen units to a block, y = 24 at ground level, the axis pointing down. The
// top sits at y = 8, so the model is exactly one block tall and matches the
// hitbox instead of floating above it.
public class ArgilusModel extends EntityModel<LivingEntityRenderState> {
	private final ModelPart head;
	private final ModelPart rightArm;
	private final ModelPart leftArm;
	private final ModelPart rightLeg;
	private final ModelPart leftLeg;

	public ArgilusModel(ModelPart root) {
		super(root);
		this.head = root.getChild(PartNames.HEAD);
		this.rightArm = root.getChild(PartNames.RIGHT_ARM);
		this.leftArm = root.getChild(PartNames.LEFT_ARM);
		this.rightLeg = root.getChild(PartNames.RIGHT_LEG);
		this.leftLeg = root.getChild(PartNames.LEFT_LEG);
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

		root.addOrReplaceChild(
				PartNames.HEAD,
				CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -5.0F, -3.0F, 6.0F, 5.0F, 6.0F),
				PartPose.offset(0.0F, 13.0F, 0.0F));

		root.addOrReplaceChild(
				PartNames.BODY,
				CubeListBuilder.create().texOffs(0, 16).addBox(-3.0F, 0.0F, -2.0F, 6.0F, 7.0F, 4.0F),
				PartPose.offset(0.0F, 13.0F, 0.0F));

		root.addOrReplaceChild(
				PartNames.RIGHT_ARM,
				CubeListBuilder.create().texOffs(24, 16).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 7.0F, 2.0F),
				PartPose.offset(-4.0F, 14.0F, 0.0F));

		root.addOrReplaceChild(
				PartNames.LEFT_ARM,
				CubeListBuilder.create().texOffs(24, 32).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 7.0F, 2.0F),
				PartPose.offset(4.0F, 14.0F, 0.0F));

		root.addOrReplaceChild(
				PartNames.RIGHT_LEG,
				CubeListBuilder.create().texOffs(40, 16).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F),
				PartPose.offset(-1.5F, 20.0F, 0.0F));

		root.addOrReplaceChild(
				PartNames.LEFT_LEG,
				CubeListBuilder.create().texOffs(40, 32).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F),
				PartPose.offset(1.5F, 20.0F, 0.0F));

		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(LivingEntityRenderState state) {
		super.setupAnim(state);

		float swing = Mth.cos(state.walkAnimationPos * 0.6662F) * state.walkAnimationSpeed;

		this.rightLeg.xRot = swing;
		this.leftLeg.xRot = -swing;
		this.rightArm.xRot = -swing;
		this.leftArm.xRot = swing;

		this.head.yRot = state.yRot * (float) (Math.PI / 180.0);
		this.head.xRot = state.xRot * (float) (Math.PI / 180.0);
	}
}
