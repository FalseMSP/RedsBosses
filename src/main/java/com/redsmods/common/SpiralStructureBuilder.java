package com.redsmods.common;

import com.redsmods.RedsBosses;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.DataInputStream;
import java.util.zip.GZIPInputStream;
import java.util.*;

public class SpiralStructureBuilder {
    private final ServerLevel level;
    private final BlockPos centerPos;
    private final int totalDurationTicks;
    private final String blueprintFilePath;

    private List<BlockPlacement> sortedBlocks;
    private int currentTick = 0;
    private int currentBlockIndex = 0;
    private boolean isComplete = false;
    private boolean isInitialized = false;

    public SpiralStructureBuilder(ServerLevel level, BlockPos centerPos, String blueprintFilePath, int durationTicks) {
        this.level = level;
        this.centerPos = centerPos;
        this.blueprintFilePath = blueprintFilePath;
        this.totalDurationTicks = durationTicks;
    }

    /**
     * Call this method every tick. Returns true when the building animation is complete.
     */
    public boolean tick() {
        if (isComplete) {
            return true;
        }

        // Initialize on first tick
        if (!isInitialized) {
            try {
                loadAndProcessBlueprint();
                isInitialized = true;
            } catch (Exception e) {
                System.err.println("Failed to load blueprint: " + e.getMessage());
                e.printStackTrace();
                isComplete = true;
                return true;
            }
        }

        currentTick++;

        // Calculate progress and how many blocks should be placed
        float progress = Math.min(1.0f, (float) currentTick / totalDurationTicks);
        int targetBlockCount = Math.round(progress * sortedBlocks.size());

        // Place blocks smoothly up to target count
        while (currentBlockIndex < targetBlockCount && currentBlockIndex < sortedBlocks.size()) {
            BlockPlacement placement = sortedBlocks.get(currentBlockIndex);

            // Only place non-air blocks
//            if (!placement.blockState.isAir()) {
                level.setBlock(placement.pos, placement.blockState, 3);
//            }

            currentBlockIndex++;
        }

        // Check if complete
        if (currentBlockIndex >= sortedBlocks.size()) {
            isComplete = true;
            return true;
        }

        return false;
    }

    private void loadAndProcessBlueprint() throws IOException {
        CompoundTag blueprintData = null;

        System.out.println("Attempting to load blueprint from: " + blueprintFilePath);

        // Check if it's a resource path (starts with /) or absolute file path
        if (blueprintFilePath.startsWith("/") || blueprintFilePath.startsWith("assets/")) {
            // Load from mod resources
            String resourcePath = blueprintFilePath.startsWith("/") ? blueprintFilePath.substring(1) : blueprintFilePath;
            if (!resourcePath.startsWith("assets/")) {
                resourcePath = "assets/" + RedsBosses.MODID + "/schematics/" + resourcePath;
            }

            System.out.println("Loading from resource path: " + resourcePath);

            try (var inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                if (inputStream == null) {
                    throw new IOException("Blueprint resource not found: " + resourcePath);
                }

                System.out.println("Resource found, attempting to read blueprint data...");
                blueprintData = loadBlueprintFromStream(inputStream);
            }
        } else {
            // Load from file system (absolute path)
            File blueprintFile = new File(blueprintFilePath);
            System.out.println("Loading from file: " + blueprintFile.getAbsolutePath());

            if (!blueprintFile.exists()) {
                throw new IOException("Blueprint file not found: " + blueprintFilePath);
            }

            System.out.println("File exists (" + blueprintFile.length() + " bytes), attempting to read blueprint data...");

            try (FileInputStream fis = new FileInputStream(blueprintFile)) {
                blueprintData = loadBlueprintFromStream(fis);
            }
        }

        if (blueprintData == null) {
            throw new IOException("Failed to load NBT data from blueprint");
        }

        System.out.println("Blueprint NBT root keys: " + blueprintData.getAllKeys());

        // Parse blueprint data
        BlueprintData blueprint = parseBlueprint(blueprintData);

        if (blueprint == null) {
            throw new IOException("Failed to parse blueprint data");
        }

        System.out.println("Blueprint parsed successfully: " + blueprint.width + "x" + blueprint.height + "x" + blueprint.length);

        // Calculate spiral order from center
        this.sortedBlocks = calculateSpiralOrder(blueprint);

        System.out.println("Spiral order calculated: " + sortedBlocks.size() + " blocks to place");
    }

    private CompoundTag loadBlueprintFromStream(java.io.InputStream inputStream) throws IOException {
        // Read the first few bytes to determine format
        byte[] header = new byte[8];
        inputStream.mark(8);
        int bytesRead = inputStream.read(header);
        inputStream.reset();

        System.out.println("File header bytes: " + Arrays.toString(Arrays.copyOf(header, Math.min(bytesRead, 8))));

        // Check if it's gzipped (.schem files are typically gzipped)
        if (bytesRead >= 2 && header[0] == (byte)0x1f && header[1] == (byte)0x8b) {
            System.out.println("Detected gzipped format (.schem), decompressing...");
            try (GZIPInputStream gzis = new GZIPInputStream(inputStream);
                 DataInputStream dis = new DataInputStream(gzis)) {
                return NbtIo.read(dis);
            }
        }

        // Try as uncompressed NBT
        try {
            System.out.println("Trying uncompressed NBT format...");
            try (DataInputStream dis = new DataInputStream(inputStream)) {
                return NbtIo.read(dis);
            }
        } catch (Exception e) {
            System.out.println("Uncompressed NBT failed: " + e.getMessage());

            // Reset stream and try as compressed NBT
            inputStream.reset();
            try {
                System.out.println("Trying compressed NBT format...");
                throw new IOException("failed compressed");
            } catch (Exception e2) {
                System.out.println("Compressed NBT failed: " + e2.getMessage());
                throw new IOException("Unable to parse blueprint file. Tried gzipped, uncompressed, and compressed NBT formats. Original error: " + e.getMessage());
            }
        }
    }

    private BlueprintData parseBlueprint(CompoundTag nbt) {
        try {
            System.out.println("Parsing blueprint...");
            System.out.println("Root NBT keys: " + nbt.getAllKeys());

            // Check for Sponge Schematic format (.schem)
            if (nbt.contains("Schematic") || (nbt.contains("Width") && nbt.contains("Height") && nbt.contains("Length") && nbt.contains("Palette"))) {
                return parseSpongeSchematic(nbt);
            }

            // Check for WorldEdit schematic format (.schematic)
            if (nbt.contains("width") && nbt.contains("height") && nbt.contains("length")) {
                return parseWorldEditSchematic(nbt);
            }

            // Try other formats as fallback
            return parseGenericFormat(nbt);

        } catch (Exception e) {
            System.err.println("Error parsing blueprint: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private BlueprintData parseSpongeSchematic(CompoundTag nbt) {
        System.out.println("Parsing Sponge Schematic format (.schem)");

        // Handle nested "Schematic" tag if present
        CompoundTag schematicTag = nbt.contains("Schematic") ? nbt.getCompound("Schematic") : nbt;

        // Get dimensions
        short width = schematicTag.getShort("Width");
        short height = schematicTag.getShort("Height");
        short length = schematicTag.getShort("Length");

        System.out.println("Schematic dimensions: " + width + "x" + height + "x" + length);

        if (width <= 0 || height <= 0 || length <= 0) {
            System.err.println("Invalid schematic dimensions");
            return null;
        }

        BlueprintData blueprint = new BlueprintData(width, height, length);

        // Parse palette
        Map<Integer, BlockState> palette = new HashMap<>();
        if (schematicTag.contains("Palette")) {
            CompoundTag paletteTag = schematicTag.getCompound("Palette");
            System.out.println("Parsing palette with " + paletteTag.size() + " entries...");

            for (String blockName : paletteTag.getAllKeys()) {
                int paletteId = paletteTag.getInt(blockName);
                BlockState state = parseBlockState(blockName);
                palette.put(paletteId, state);
                System.out.println("Palette entry " + paletteId + ": " + blockName + " -> " + state);
            }
        }

        // Parse block data
        if (schematicTag.contains("BlockData")) {
            byte[] blockData = schematicTag.getByteArray("BlockData");
            System.out.println("Block data length: " + blockData.length + " bytes");

            // Sponge format uses variable-length integers
            int index = 0;
            int blockIndex = 0;

            while (index < blockData.length && blockIndex < width * height * length) {
                int paletteId = readVarInt(blockData, index);
                index += getVarIntSize(paletteId);

                int x = blockIndex % width;
                int y = (blockIndex / width) / length;
                int z = (blockIndex / width) % length;

                BlockState state = palette.getOrDefault(paletteId, Blocks.AIR.defaultBlockState());
                blueprint.setBlock(x, y, z, state);

                blockIndex++;
            }
        }

        return blueprint;
    }

    private BlueprintData parseWorldEditSchematic(CompoundTag nbt) {
        System.out.println("Parsing WorldEdit Schematic format (.schematic)");

        short width = nbt.getShort("width");
        short height = nbt.getShort("height");
        short length = nbt.getShort("length");

        System.out.println("Schematic dimensions: " + width + "x" + height + "x" + length);

        BlueprintData blueprint = new BlueprintData(width, height, length);

        // Handle WorldEdit schematic format
        if (nbt.contains("blocks")) {
            byte[] blocks = nbt.getByteArray("blocks");
            byte[] data = nbt.contains("data") ? nbt.getByteArray("data") : new byte[blocks.length];

            int index = 0;
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < length; z++) {
                    for (int x = 0; x < width; x++) {
                        if (index < blocks.length) {
                            int blockId = blocks[index] & 0xFF;
                            int blockData = index < data.length ? data[index] & 0xFF : 0;
                            BlockState state = getBlockStateFromLegacyId(blockId, blockData);
                            blueprint.setBlock(x, y, z, state);
                            index++;
                        }
                    }
                }
            }
        }

        return blueprint;
    }

    private BlueprintData parseGenericFormat(CompoundTag nbt) {
        System.out.println("Attempting to parse as generic format");

        // Try to find dimensions
        int width = 0, height = 0, length = 0;

        String[] widthKeys = {"width", "Width", "sizeX", "w"};
        String[] heightKeys = {"height", "Height", "sizeY", "h"};
        String[] lengthKeys = {"length", "Length", "sizeZ", "l", "depth", "Depth"};

        for (String key : widthKeys) {
            if (nbt.contains(key)) {
                width = nbt.getInt(key);
                break;
            }
        }

        for (String key : heightKeys) {
            if (nbt.contains(key)) {
                height = nbt.getInt(key);
                break;
            }
        }

        for (String key : lengthKeys) {
            if (nbt.contains(key)) {
                length = nbt.getInt(key);
                break;
            }
        }

        if (width <= 0 || height <= 0 || length <= 0) {
            System.err.println("Could not determine valid dimensions from generic format");
            return null;
        }

        System.out.println("Generic format dimensions: " + width + "x" + height + "x" + length);
        return new BlueprintData(width, height, length);
    }

    private BlockState parseBlockState(String blockString) {
        try {
            // Handle format like "minecraft:stone" or "minecraft:oak_stairs[facing=north,half=bottom]"
            String[] parts = blockString.split("\\[", 2);
            String blockId = parts[0];

            // Parse the block
            ResourceLocation resourceLocation = ResourceLocation.parse(blockId);
            Block block = BuiltInRegistries.BLOCK.get(resourceLocation);

            if (block == null || block == Blocks.AIR) {
                System.err.println("Unknown block: " + blockId);
                return Blocks.AIR.defaultBlockState();
            }

            BlockState state = block.defaultBlockState();

            // Parse properties if present
            if (parts.length > 1) {
                String propertiesString = parts[1].replace("]", "");
                state = parseBlockProperties(state, propertiesString);
            }

            return state;
        } catch (Exception e) {
            System.err.println("Failed to parse block state from: " + blockString + " - " + e.getMessage());
            return Blocks.AIR.defaultBlockState();
        }
    }

    @SuppressWarnings("unchecked")
    private BlockState parseBlockProperties(BlockState baseState, String propertiesString) {
        if (propertiesString.isEmpty()) {
            return baseState;
        }

        BlockState state = baseState;
        String[] properties = propertiesString.split(",");

        for (String propertyPair : properties) {
            String[] keyValue = propertyPair.split("=", 2);
            if (keyValue.length == 2) {
                String key = keyValue[0].trim();
                String value = keyValue[1].trim();

                try {
                    Property property = null;
                    // Find the property by name
                    for (Property prop : state.getProperties()) {
                        if (prop.getName().equals(key)) {
                            property = prop;
                            break;
                        }
                    }

                    if (property != null) {
                        Optional<?> parsedValue = property.getValue(value);
                        if (parsedValue.isPresent()) {
                            state = state.setValue(property, (Comparable) parsedValue.get());
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Failed to parse property " + key + "=" + value + ": " + e.getMessage());
                }
            }
        }

        return state;
    }

    private BlockState getBlockStateFromLegacyId(int blockId, int data) {
        // Convert legacy block IDs to modern block states
        // This is a simplified conversion - you'd need a full mapping table for complete accuracy
        switch (blockId) {
            case 0: return Blocks.AIR.defaultBlockState();
            case 1:
                switch (data) {
                    case 1: return Blocks.GRANITE.defaultBlockState();
                    case 2: return Blocks.POLISHED_GRANITE.defaultBlockState();
                    case 3: return Blocks.DIORITE.defaultBlockState();
                    case 4: return Blocks.POLISHED_DIORITE.defaultBlockState();
                    case 5: return Blocks.ANDESITE.defaultBlockState();
                    case 6: return Blocks.POLISHED_ANDESITE.defaultBlockState();
                    default: return Blocks.STONE.defaultBlockState();
                }
            case 2: return Blocks.GRASS_BLOCK.defaultBlockState();
            case 3:
                switch (data) {
                    case 1: return Blocks.COARSE_DIRT.defaultBlockState();
                    case 2: return Blocks.PODZOL.defaultBlockState();
                    default: return Blocks.DIRT.defaultBlockState();
                }
            case 4: return Blocks.COBBLESTONE.defaultBlockState();
            case 5:
                switch (data) {
                    case 1: return Blocks.SPRUCE_PLANKS.defaultBlockState();
                    case 2: return Blocks.BIRCH_PLANKS.defaultBlockState();
                    case 3: return Blocks.JUNGLE_PLANKS.defaultBlockState();
                    case 4: return Blocks.ACACIA_PLANKS.defaultBlockState();
                    case 5: return Blocks.DARK_OAK_PLANKS.defaultBlockState();
                    default: return Blocks.OAK_PLANKS.defaultBlockState();
                }
            case 6: return Blocks.OAK_SAPLING.defaultBlockState(); // Simplified
            case 7: return Blocks.BEDROCK.defaultBlockState();
            case 8: case 9: return Blocks.WATER.defaultBlockState();
            case 10: case 11: return Blocks.LAVA.defaultBlockState();
            case 12: return Blocks.SAND.defaultBlockState();
            case 13: return Blocks.GRAVEL.defaultBlockState();
            case 14: return Blocks.GOLD_ORE.defaultBlockState();
            case 15: return Blocks.IRON_ORE.defaultBlockState();
            case 16: return Blocks.COAL_ORE.defaultBlockState();
            case 17: return Blocks.OAK_LOG.defaultBlockState(); // Simplified
            case 18: return Blocks.OAK_LEAVES.defaultBlockState(); // Simplified
            case 19: return Blocks.SPONGE.defaultBlockState();
            case 20: return Blocks.GLASS.defaultBlockState();
            // Add more mappings as needed...
            default:
                System.err.println("Unknown legacy block ID: " + blockId + " with data: " + data);
                return Blocks.STONE.defaultBlockState();
        }
    }

    private List<BlockPlacement> calculateSpiralOrder(BlueprintData blueprint) {
        List<BlockPlacement> blocks = new ArrayList<>();

        // Collect all blocks with their positions
        for (int x = 0; x < blueprint.width; x++) {
            for (int y = 0; y < blueprint.height; y++) {
                for (int z = 0; z < blueprint.length; z++) {
                    BlockState state = blueprint.getBlock(x, y, z);
                    if (state != null) {
                        BlockPos worldPos = centerPos.offset(
                                x - blueprint.width / 2,
                                y,
                                z - blueprint.length / 2
                        );
                        blocks.add(new BlockPlacement(worldPos, state, x, y, z));
                    }
                }
            }
        }

        // Sort blocks in spiral order from center
        blocks.sort((a, b) -> {
            // Calculate distance from center for spiral ordering
            int centerX = blueprint.width / 2;
            int centerZ = blueprint.length / 2;

            double distA = Math.sqrt(Math.pow(a.blueprintX - centerX, 2) + Math.pow(a.blueprintZ - centerZ, 2));
            double distB = Math.sqrt(Math.pow(b.blueprintX - centerX, 2) + Math.pow(b.blueprintZ - centerZ, 2));

            // Primary sort by distance from center
            int distCompare = Double.compare(distA, distB);
            if (distCompare != 0) return distCompare;

            // Secondary sort by Y level (bottom to top)
            int yCompare = Integer.compare(a.blueprintY, b.blueprintY);
            if (yCompare != 0) return yCompare;

            // Tertiary sort by angle for true spiral effect
            double angleA = Math.atan2(a.blueprintZ - centerZ, a.blueprintX - centerX);
            double angleB = Math.atan2(b.blueprintZ - centerZ, b.blueprintX - centerX);
            return Double.compare(angleA, angleB);
        });

        return blocks;
    }

    private int readVarInt(byte[] data, int startIndex) {
        int value = 0;
        int position = 0;
        byte currentByte;
        int index = startIndex;

        while (true) {
            if (index >= data.length) break;

            currentByte = data[index++];
            value |= (currentByte & 0x7F) << position;

            if ((currentByte & 0x80) == 0) break;

            position += 7;
            if (position >= 32) break; // Prevent overflow
        }

        return value;
    }

    private int getVarIntSize(int value) {
        int size = 0;
        do {
            value >>>= 7;
            size++;
        } while (value != 0);
        return size;
    }

    public boolean isComplete() {
        return isComplete;
    }

    public float getProgress() {
        if (sortedBlocks == null || sortedBlocks.isEmpty()) return 0.0f;
        return (float) currentBlockIndex / sortedBlocks.size();
    }

    // Helper classes
    private static class BlueprintData {
        public final int width, height, length;
        private final BlockState[][][] blocks;

        public BlueprintData(int width, int height, int length) {
            this.width = width;
            this.height = height;
            this.length = length;
            this.blocks = new BlockState[width][height][length];
        }

        public void setBlock(int x, int y, int z, BlockState state) {
            if (x >= 0 && x < width && y >= 0 && y < height && z >= 0 && z < length) {
                blocks[x][y][z] = state;
            }
        }

        public BlockState getBlock(int x, int y, int z) {
            if (x >= 0 && x < width && y >= 0 && y < height && z >= 0 && z < length) {
                return blocks[x][y][z];
            }
            return null;
        }
    }

    private static class BlockPlacement {
        public final BlockPos pos;
        public final BlockState blockState;
        public final int blueprintX, blueprintY, blueprintZ;

        public BlockPlacement(BlockPos pos, BlockState blockState, int blueprintX, int blueprintY, int blueprintZ) {
            this.pos = pos;
            this.blockState = blockState;
            this.blueprintX = blueprintX;
            this.blueprintY = blueprintY;
            this.blueprintZ = blueprintZ;
        }
    }
}