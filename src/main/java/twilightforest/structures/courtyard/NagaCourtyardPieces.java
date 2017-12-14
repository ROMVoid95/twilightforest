package twilightforest.structures.courtyard;

import net.minecraft.world.gen.structure.MapGenStructureIO;

public class NagaCourtyardPieces {
    public static void registerPieces() {
        MapGenStructureIO.registerStructureComponent(ComponentNagaCourtyardMain.class, "TFNCMn");
        MapGenStructureIO.registerStructureComponent(ComponentNagaCourtyardCap.class, "TFNCCp");
        MapGenStructureIO.registerStructureComponent(ComponentNagaCourtyardCorner.class, "TFNCCr");
        MapGenStructureIO.registerStructureComponent(ComponentNagaCourtyardLine.class, "TFNCLn");
        MapGenStructureIO.registerStructureComponent(ComponentNagaCourtyardTJunction.class, "TFNCT");
        MapGenStructureIO.registerStructureComponent(ComponentNagaCourtyardIntersection.class, "TFNCIs");
        MapGenStructureIO.registerStructureComponent(ComponentNagaCourtyardPadder.class, "TFNCPd");
        MapGenStructureIO.registerStructureComponent(ComponentNagaCourtyardTerrace.class, "TFNCTr");
        MapGenStructureIO.registerStructureComponent(ComponentNagaCourtyardPath.class, "TFNCPa");
        MapGenStructureIO.registerStructureComponent(ComponentNagaCourtyardWall.class, "TFNCWl");
        MapGenStructureIO.registerStructureComponent(ComponentNagaCourtyardWallPadder.class, "TFNCWP");
        MapGenStructureIO.registerStructureComponent(ComponentNagaCourtyardWallCorner.class, "TFNCWC");
        MapGenStructureIO.registerStructureComponent(ComponentNagaCourtyardWallCornerAlt.class, "TFNCWA");
    }
}