package net.earthcomputer.quarrybreaker;

import com.mumfrey.liteloader.LiteMod;
import com.mumfrey.liteloader.PostRenderListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.command.CommandBase;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LiteModQuarryBreaker implements LiteMod, PostRenderListener {

    private static int hashSize = 128;
    private static final Set<BlockPos> trackingPositions = new HashSet<>();
    private static final Map<ChunkPos, List<Pair<BlockPos, BlockWarningLevel>>> blockWarningCache = new HashMap<>();


    @Override
    public String getName() {
        return "Quarry Breaker";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public void init(File configPath) {
    }

    @Override
    public void upgradeSettings(String version, File configPath, File oldConfigPath) {
    }

    public static BlockWarningLevel getWarningLevel(BlockPos posToCheck) {
        int hash = posToCheck.hashCode();
        hash ^= hash >>> 16;
        int hashModHashSize = hash & (hashSize - 1);

        BlockWarningLevel warningLevel = BlockWarningLevel.NONE;
        for (BlockPos pos : trackingPositions) {
            int h = pos.hashCode();
            h ^= h >>> 16;
            int hModHashSize = h & (hashSize - 1);
            if (hash == h) {
                warningLevel = BlockWarningLevel.SAME;
            } else if (hashModHashSize == hModHashSize && warningLevel != BlockWarningLevel.SAME) {
                warningLevel = BlockWarningLevel.SAME_MOD_HASH;
            }
        }

        return warningLevel;
    }

    public static void handleQuarryBreakerCommand(String[] args) {
        final String USAGE = "/quarrybreaker <add|remove|hashsize> ...";
        EntityPlayerSP player = Minecraft.getMinecraft().player;

        if (args.length == 0) {
            player.sendMessage(new TextComponentString(TextFormatting.RED + USAGE));
            return;
        }

        switch (args[0]) {
            case "add":
            case "remove": {
                if (args.length < 4) {
                    player.sendMessage(new TextComponentString(String.format("%s/quarrybreaker <%s> <x> <y> <z>", TextFormatting.RED, args[0])));
                    return;
                }
                int x, y, z;
                try {
                    x = Integer.parseInt(args[1]);
                    y = Integer.parseInt(args[2]);
                    z = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    player.sendMessage(new TextComponentString("Invalid coordinates"));
                    return;
                }
                BlockPos pos = new BlockPos(x, y, z);
                if ("add".equals(args[0])) {
                    trackingPositions.add(pos);
                    player.sendMessage(new TextComponentString(String.format("Tracking (%d, %d, %d)", x, y, z)));
                } else {
                    trackingPositions.remove(pos);
                    player.sendMessage(new TextComponentString(String.format("No longer tracking (%d, %d, %d)", x, y, z)));
                }
                blockWarningCache.clear();
            }
            break;
            case "hashsize": {
                if (args.length == 1) {
                    sendHashSizeMessage(player, "is currently");
                    return;
                }
                int newHashSize;
                try {
                    newHashSize = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    player.sendMessage(new TextComponentString(TextFormatting.RED + "Invalid hash size"));
                    return;
                }
                if ((newHashSize & -newHashSize) != newHashSize) {
                    player.sendMessage(new TextComponentString(TextFormatting.RED + "Hash size must be a power of 2"));
                    return;
                }
                if (newHashSize != hashSize) {
                    hashSize = newHashSize;
                    blockWarningCache.clear();
                }
                sendHashSizeMessage(player, "has been updated to");
            }
            break;
            default: {
                player.sendMessage(new TextComponentString(TextFormatting.RED + USAGE));
            }
            break;
        }
    }

    private static void sendHashSizeMessage(EntityPlayerSP player, String action) {
        player.sendMessage(new TextComponentString(String.format("Hash size %s %d.", action, hashSize)));
        player.sendMessage(new TextComponentString("Use /quarrybreaker hashsize <hashsize> to change it."));
        player.sendMessage(new TextComponentString(String.format("A hash size of %d means you need to activate %d tile ticks in the same tick before the visualization is correct.", hashSize, hashSize * 3 / 4)));
    }

    public static List<String> tabCompleteQuarryBreakerCommand(String[] args, BlockPos targetBlockPos) {
        if (args.length == 1) {
            return CommandBase.getListOfStringsMatchingLastWord(args, "add", "remove", "hashsize");
        } else if (args.length >= 2 && args.length <= 4) {
            if (("add".equals(args[0]) || "remove".equals(args[0])) && targetBlockPos != null) {
                return CommandBase.getTabCompletionCoordinate(args, 1, targetBlockPos);
            }
        }

        return Collections.emptyList();
    }

    @Override
    public void onPostRenderEntities(float partialTicks) {
    }

    @Override
    public void onPostRender(float partialTicks) {
        Entity viewEntity = Minecraft.getMinecraft().getRenderViewEntity();
        if (viewEntity == null) {
            return;
        }

        GlStateManager.enableBlend();
        GlStateManager.disableDepth();
        GlStateManager.disableCull();
        GlStateManager.disableTexture2D();
        GlStateManager.pushMatrix();
        GlStateManager.translate(
                -(viewEntity.prevPosX + (viewEntity.posX - viewEntity.prevPosX) * partialTicks),
                -(viewEntity.prevPosY + (viewEntity.posY - viewEntity.prevPosY) * partialTicks),
                -(viewEntity.prevPosZ + (viewEntity.posZ - viewEntity.prevPosZ) * partialTicks));

        BlockPos viewEntityPos = new BlockPos(viewEntity);
        int cx = viewEntityPos.getX() >> 4;
        int cz = viewEntityPos.getZ() >> 4;

        for (int dcx = -3; dcx <= 3; dcx++) {
            for (int dcz = -3; dcz <= 3; dcz++) {
                ChunkPos cp = new ChunkPos(cx + dcx, cz + dcz);
                List<Pair<BlockPos, BlockWarningLevel>> positions = blockWarningCache.computeIfAbsent(cp, _cp -> {
                    List<Pair<BlockPos, BlockWarningLevel>> posits = new ArrayList<>();
                    for (BlockPos pos : BlockPos.getAllInBox(_cp.getXStart(), 0, _cp.getZStart(), _cp.getXEnd(), 255,
                            _cp.getZEnd())) {
                        BlockWarningLevel warningLevel = getWarningLevel(pos);
                        if (warningLevel != BlockWarningLevel.NONE) {
                            posits.add(Pair.of(pos, warningLevel));
                        }
                    }
                    return posits;
                });
                for (Pair<BlockPos, BlockWarningLevel> position : positions) {
                    BlockPos pos = position.getLeft();
                    BlockWarningLevel warningLevel = position.getRight();
                    RenderGlobal.drawBoundingBox(pos.getX(), pos.getY(), pos.getZ(),
                            pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1,
                            1, warningLevel == BlockWarningLevel.SAME ? 0 : 0.5f, 0, 0.5f);
                }
            }
        }
        GlStateManager.popMatrix();
        GlStateManager.enableTexture2D();
        GlStateManager.enableCull();
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();

        // clean cache
        blockWarningCache.keySet().removeIf(cp -> Math.abs(cp.x - cx) > 4 || Math.abs(cp.z - cz) > 4);
    }
}
