package twilightforest.structures.start;

import net.minecraft.world.World;
import net.minecraft.world.gen.feature.structure.StructurePiece;
import twilightforest.TFFeature;
import twilightforest.structures.icetower.ComponentTFIceTowerMain;

import java.util.Random;

import static twilightforest.TFFeature.ICE_TOWER;

public class StructureStartAuroraPalace extends StructureStartTFFeatureAbstract {
//    public StructureStartAuroraPalace() {
//        super();
//    }

    public StructureStartAuroraPalace(World world, TFFeature feature, Random rand, int chunkX, int chunkZ) {
        super(world, feature, rand, chunkX, chunkZ);
    }

    @Override
    protected StructurePiece makeFirstComponent(TFFeature feature, Random rand, int x, int y, int z) {
        return new ComponentTFIceTowerMain(ICE_TOWER, rand, 0, x, y, z);
    }
}