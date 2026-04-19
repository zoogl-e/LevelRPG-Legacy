package net.zoogle.levelrpg.client.model;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.LevelRPG;

/**
 * Book model exported from Blockbench, adapted for GUI rendering.
 */
public class BookModel {
    // This layer location should be baked during client init and passed into this model's constructor
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "book"), "main");

    private final ModelPart root;
    public final ModelPart Book;
    public final ModelPart Page12;
    public final ModelPart CoverGroup;
    public final ModelPart Page1;
    public final ModelPart Page2;
    public final ModelPart Page3;
    public final ModelPart Page4;
    public final ModelPart Page5;
    public final ModelPart Page6;
    public final ModelPart Page7;
    public final ModelPart Page8;
    public final ModelPart Page9;
    public final ModelPart Page10;
    public final ModelPart Page11;

    public BookModel(ModelPart root) {
        this.root = root;
        this.Book = root.getChild("Book");
        this.Page12 = this.Book.getChild("Page12");
        this.CoverGroup = this.Book.getChild("CoverGroup");
        this.Page1 = this.Book.getChild("Page1");
        this.Page2 = this.Book.getChild("Page2");
        this.Page3 = this.Book.getChild("Page3");
        this.Page4 = this.Book.getChild("Page4");
        this.Page5 = this.Book.getChild("Page5");
        this.Page6 = this.Book.getChild("Page6");
        this.Page7 = this.Book.getChild("Page7");
        this.Page8 = this.Book.getChild("Page8");
        this.Page9 = this.Book.getChild("Page9");
        this.Page10 = this.Book.getChild("Page10");
        this.Page11 = this.Book.getChild("Page11");
    }

    public ModelPart root() { return root; }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition Book = partdefinition.addOrReplaceChild("Book", CubeListBuilder.create(), PartPose.offset(-41.5F, -26.0F, 5.0F));

        Book.addOrReplaceChild("Back_r1", CubeListBuilder.create().texOffs(86, 0).addBox(-41.5F, -51.0F, -1.0F, 83.0F, 102.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 3.1416F, 0.0F));


        PartDefinition CoverGroup = Book.addOrReplaceChild("CoverGroup", CubeListBuilder.create().texOffs(0, 0).addBox(1.0F, -101.0F, -8.0F, 82.0F, 101.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(-41.5F, 50.0F, 0.0F));

        CoverGroup.addOrReplaceChild("Binding_r1", CubeListBuilder.create().texOffs(69, 0).addBox(-1.0F, -101.0F, -1.0F, 9.0F, 101.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 1.5708F, 0.0F));

        Book.addOrReplaceChild("Page1", CubeListBuilder.create().texOffs(88, 104).addBox(0.0F, -99.0F, -1.0F, 80.0F, 95.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(-40.5F, 50.0F, -6.0F));
        Book.addOrReplaceChild("Page2", CubeListBuilder.create().texOffs(88, 104).addBox(0.0F, -99.0F, -1.0F, 80.0F, 95.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(-40.5F, 50.0F, -5.5F));
        Book.addOrReplaceChild("Page3", CubeListBuilder.create().texOffs(88, 104).addBox(0.0F, -99.0F, -1.0F, 80.0F, 95.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(-40.5F, 50.0F, -5.0F));
        Book.addOrReplaceChild("Page4", CubeListBuilder.create().texOffs(88, 104).addBox(0.0F, -99.0F, -1.0F, 80.0F, 95.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(-40.5F, 50.0F, -4.5F));
        Book.addOrReplaceChild("Page5", CubeListBuilder.create().texOffs(88, 104).addBox(0.0F, -99.0F, -1.0F, 80.0F, 95.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(-40.5F, 50.0F, -4.0F));
        Book.addOrReplaceChild("Page6", CubeListBuilder.create().texOffs(88, 104).addBox(0.0F, -99.0F, -1.0F, 80.0F, 95.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(-40.5F, 50.0F, -3.5F));
        Book.addOrReplaceChild("Page7", CubeListBuilder.create().texOffs(88, 104).addBox(0.0F, -99.0F, -1.0F, 80.0F, 95.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(-40.5F, 50.0F, -3.0F));
        Book.addOrReplaceChild("Page8", CubeListBuilder.create().texOffs(88, 104).addBox(0.0F, -99.0F, -1.0F, 80.0F, 95.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(-40.5F, 50.0F, -2.5F));
        Book.addOrReplaceChild("Page9", CubeListBuilder.create().texOffs(88, 104).addBox(0.0F, -99.0F, -1.0F, 80.0F, 95.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(-40.5F, 50.0F, -2.0F));
        Book.addOrReplaceChild("Page10", CubeListBuilder.create().texOffs(88, 104).addBox(0.0F, -99.0F, -1.0F, 80.0F, 95.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(-40.5F, 50.0F, -1.5F));
        Book.addOrReplaceChild("Page11", CubeListBuilder.create().texOffs(88, 104).addBox(0.0F, -99.0F, -1.0F, 80.0F, 95.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(-40.5F, 50.0F, -1.0F));
        Book.addOrReplaceChild("Page12", CubeListBuilder.create().texOffs(88, 104).addBox(0.0F, -99.0F, -1.0F, 80.0F, 95.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(-40.5F, 50.0F, -0.5F));


        return LayerDefinition.create(meshdefinition, 256, 256);
    }
}