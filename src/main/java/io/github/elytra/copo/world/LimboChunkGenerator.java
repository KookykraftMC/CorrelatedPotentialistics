package io.github.elytra.copo.world;

import java.util.Collections;
import java.util.List;

import io.github.elytra.copo.math.Vec2f;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome.SpawnListEntry;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkGenerator;

public class LimboChunkGenerator implements IChunkGenerator {
	private final World world;
	private final DungeonGrid grid;
	
	public LimboChunkGenerator(World world, boolean mapFeaturesEnabled, long seed, DungeonGrid grid) {
		this.world = world;
		this.grid = grid;
	}

	@Override
	public Chunk provideChunk(int x, int z) {
		return new Chunk(world, x, z);
	}

	@Override
	public void populate(int x, int z) {
	}

	@Override
	public boolean generateStructures(Chunk chunkIn, int x, int z) {
		return false;
	}

	@Override
	public List<SpawnListEntry> getPossibleCreatures(EnumCreatureType creatureType, BlockPos pos) {
		return Collections.emptyList();
	}

	@Override
	public BlockPos getStrongholdGen(World worldIn, String structureName, BlockPos position) {
		Dungeon d = grid.getFromBlock(position);
		if (d != null) {
			Vec2f entrance = d.findEntranceTile();
			int x = ((d.x*Dungeon.NODE_SIZE)*Dungeon.DUNGEON_SIZE);
			int z = ((d.z*Dungeon.NODE_SIZE)*Dungeon.DUNGEON_SIZE);
			x += entrance.x*Dungeon.NODE_SIZE;
			z += entrance.y*Dungeon.NODE_SIZE;
			return new BlockPos(x, 60, z);
		}
		return null;
	}

	@Override
	public void recreateStructures(Chunk chunkIn, int x, int z) {

	}

}
