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

// Geometry modelled in tools/argilus.bbmodel and transcribed here, box for box.
// Edit the model there, not the numbers below, or the two drift apart; the
// texOffs values are also what tools/GenArgilus.java paints into.
//
// Transcribing is not copying: Blockbench counts y up from the ground and puts
// +X in the first slot of a strip, where ModelPart counts y down from 24 and
// puts -X there. So y here is 24 minus y there, and x is negated - a right arm
// at x 5 in Blockbench is at -5 below. Miss the sign and every texture comes
// out mirrored.
//
// Sixteen units to a block. The top of the head sits at y = 8, one block up,
// so the body fits the hitbox; the hat stands above it, as a hat does.
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

		PartDefinition head = root.addOrReplaceChild(
				PartNames.HEAD,
				CubeListBuilder.create().texOffs(0, 42).addBox(-4.0F, -4.0F, -4.0F, 8.0F, 4.0F, 8.0F),
				PartPose.offset(0.0F, 12.0F, 0.0F));

		// Children of the head, so the hat follows it without any animation code
		// of its own. Brim first, then the crown sitting on top of it.
		head.addOrReplaceChild(
				"hat_brim",
				CubeListBuilder.create().texOffs(0, 0).addBox(-7.0F, -5.0F, -7.0F, 14.0F, 1.0F, 14.0F),
				PartPose.ZERO);

		head.addOrReplaceChild(
				"hat_crown",
				CubeListBuilder.create().texOffs(30, 15).addBox(-4.0F, -7.0F, -3.5F, 8.0F, 2.0F, 7.0F),
				PartPose.ZERO);

		root.addOrReplaceChild(
				PartNames.BODY,
				CubeListBuilder.create().texOffs(0, 26).addBox(-4.0F, 0.0F, -3.0F, 8.0F, 8.0F, 6.0F),
				PartPose.offset(0.0F, 12.0F, 0.0F));

		root.addOrReplaceChild(
				PartNames.RIGHT_ARM,
				CubeListBuilder.create().texOffs(28, 26).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F),
				PartPose.offset(-5.0F, 13.0F, 0.0F));

		root.addOrReplaceChild(
				PartNames.LEFT_ARM,
				CubeListBuilder.create().texOffs(36, 26).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F),
				PartPose.offset(5.0F, 13.0F, 0.0F));

		root.addOrReplaceChild(
				PartNames.RIGHT_LEG,
				CubeListBuilder.create().texOffs(44, 26).addBox(-1.5F, 0.0F, -2.0F, 3.0F, 4.0F, 4.0F),
				PartPose.offset(-2.0F, 20.0F, 0.0F));

		root.addOrReplaceChild(
				PartNames.LEFT_LEG,
				CubeListBuilder.create().texOffs(44, 34).addBox(-1.5F, 0.0F, -2.0F, 3.0F, 4.0F, 4.0F),
				PartPose.offset(2.0F, 20.0F, 0.0F));

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
