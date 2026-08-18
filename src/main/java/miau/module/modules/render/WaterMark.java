package miau.module.modules.render;

import java.awt.Color;
import java.lang.reflect.Field;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.Render2DEvent;
import miau.module.Module;
import miau.module.modules.combat.KillAura;
import miau.property.properties.*;
import miau.util.font.Font;
import miau.util.font.FontRepository;
import miau.util.shader.RoundedUtils;
import miau.util.vector.Vector2d;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ResourceLocation;

public class WaterMark extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.00");
    private static Field curBlockDamageMPField = null;

    private static final ResourceLocation LOGO_IMAGE = new ResourceLocation("miau/logo.png");

    private static final Map<String, String> REQUIRED_TOOLS = new HashMap<>();
    static {
        REQUIRED_TOOLS.put("minecraft:wool", "Shears");
        REQUIRED_TOOLS.put("minecraft:log", "Iron Axe");
        REQUIRED_TOOLS.put("minecraft:log2", "Iron Axe");
        REQUIRED_TOOLS.put("minecraft:planks", "Iron Axe");
        REQUIRED_TOOLS.put("minecraft:end_stone", "Iron Pickaxe");
        REQUIRED_TOOLS.put("minecraft:obsidian", "Diamond Pickaxe");
    }

    public final DragProperty dragging = new DragProperty("WaterMark", new Vector2d(-1, 10));

    public final TextProperty clientName = new TextProperty("ClientName", "Miau Minus");
    public final FloatProperty animationSpeed = new FloatProperty("AnimationSpeed", 0.35F, 0.05F, 1.0F);
    public final FloatProperty scale = new FloatProperty("Scale", 100f, 15f, 100f);

    public final ModeProperty color = new ModeProperty("color", 0, new String[] {"HUD", "CUSTOM"});
    public final ColorProperty customColor = new ColorProperty("custom-color", new Color(59, 155, 240).getRGB());

    public enum State {
        Normal,
        Action
    }

    private State islandState = State.Normal;

    private float stateTransition = 0.0f;
    private float animatedProgress = 0.0f;
    private float chatAnimOffset = 0.0f;

    // --- Dynamic Island Spring Physics Variables ---
    private float springWidth = 120f;
    private float[] springWidthVel = new float[]{0f};
    private float springHeight = 20f;
    private float[] springHeightVel = new float[]{0f};

    // --- BPS Calculation Variables ---
    private float moveBps = 0f;

    private String currentActionText = "";
    private float currentActionProgress = 0;
    private EntityLivingBase currentAttackTarget = null;
    private ItemStack miningBlockStack = null;

    private long lastRenderNanoTime = 0L;

    // Scaffold state
    private boolean isScaffolding = false;
    private ItemStack scaffoldBlockStack = null;
    private int scaffoldBlocksRemaining = 0;

    public WaterMark() {
        super("WaterMark", false, true);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;

        ScaledResolution sr = new ScaledResolution(mc);

        long now = System.nanoTime();
        float deltaTime = (lastRenderNanoTime == 0L) ? (1f / 60f) : (now - lastRenderNanoTime) / 1_000_000_000f;
        deltaTime = Math.max(0f, Math.min(deltaTime, 0.1f));
        lastRenderNanoTime = now;

        // --- Calculate BPS per Minecraft delta tick ---
        double deltaX = mc.thePlayer.posX - mc.thePlayer.prevPosX;
        double deltaZ = mc.thePlayer.posZ - mc.thePlayer.prevPosZ;
        float timerSpeed = 1.0f;
        try {
            timerSpeed = ((miau.mixin.IAccessorMinecraft) mc).getTimer().timerSpeed;
        } catch (Exception ignored) {}

        float targetMoveBps = (float) (Math.hypot(deltaX, deltaZ) * 20.0D * timerSpeed);
        moveBps = smoothTowards(moveBps, targetMoveBps, 0.25f, deltaTime);

        islandState = checkPlayerAction() ? State.Action : State.Normal;

        float targetState = (islandState == State.Action) ? 1.0f : 0.0f;
        stateTransition = smoothTowards(stateTransition, targetState, animationSpeed.getValue() * 1.5f, deltaTime);
        animatedProgress = smoothTowards(animatedProgress, currentActionProgress, animationSpeed.getValue() * 1.2f, deltaTime);

        boolean isChatOpen = mc.currentScreen instanceof GuiChat;
        float targetChatOffset = isChatOpen ? -14.0f : 0.0f;
        chatAnimOffset = smoothTowards(chatAnimOffset, targetChatOffset, animationSpeed.getValue(), deltaTime);

        float renderY = 10.0f + chatAnimOffset;
        float scaleFactor = scale.getValue() / 100.0f;

        GlStateManager.pushMatrix();
        GlStateManager.scale(scaleFactor, scaleFactor, 1.0f);

        drawDynamicIsland(sr, renderY / scaleFactor, deltaTime);

        GlStateManager.popMatrix();
    }

    public float getX() {
        ScaledResolution sr = new ScaledResolution(mc);
        float scaleFactor = scale.getValue() / 100.0f;
        return sr.getScaledWidth() / 2.0f - (springWidth * scaleFactor) / 2.0f;
    }

    public float getY() {
        return 10.0f + chatAnimOffset;
    }

    public float getWidth() {
        return springWidth * (scale.getValue() / 100.0f);
    }

    public float getHeight() {
        return springHeight * (scale.getValue() / 100.0f);
    }

    public float getRadius() {
        return getHeight() / 2.0f;
    }

    private float updateSpring(float current, float target, float[] vel, float stiffness, float damping, float dt) {
        float MathDt = Math.min(dt, 0.05f);
        float force = (target - current) * stiffness;
        vel[0] = (vel[0] + force * MathDt) * (float) Math.pow(damping, MathDt * 60f);
        return current + vel[0] * MathDt;
    }

    private float smoothTowards(float current, float target, float speed, float deltaTime) {
        float clampedSpeed = Math.max(0.0001f, Math.min(1f, speed));
        float t = 1f - (float) Math.pow(1f - clampedSpeed, deltaTime * 60f);
        return current + (target - current) * t;
    }

    // --- Get NickHider username if active ---
    private String getUsername() {
        if (mc.thePlayer == null) return "";
        String defaultName = mc.thePlayer.getName();
        try {
            Module nickHider = getModuleByName("NickHider");
            if (nickHider != null && nickHider.isEnabled()) {
                for (Field f : nickHider.getClass().getDeclaredFields()) {
                    f.setAccessible(true);
                    Object val = f.get(nickHider);
                    if (val instanceof TextProperty) {
                        String text = ((TextProperty) val).getValue();
                        if (text != null && !text.trim().isEmpty()) {
                            return text;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return defaultName;
    }

    // --- Get block break damage via Reflection across parent classes & obfuscated fields ---
    private float getCurBlockDamageMP() {
        if (mc.playerController == null) return 0;
        try {
            if (curBlockDamageMPField == null) {
                Class<?> clazz = mc.playerController.getClass();
                while (clazz != null && clazz != Object.class) {
                    String[] possibleNames = new String[] {"curBlockDamageMP", "field_78770_f", "e", "f", "g"};
                    for (String name : possibleNames) {
                        try {
                            Field f = clazz.getDeclaredField(name);
                            if (f.getType() == float.class) {
                                curBlockDamageMPField = f;
                                curBlockDamageMPField.setAccessible(true);
                                break;
                            }
                        } catch (NoSuchFieldException ignored) {}
                    }
                    if (curBlockDamageMPField != null) break;
                    clazz = clazz.getSuperclass();
                }
            }

            if (curBlockDamageMPField != null) {
                return curBlockDamageMPField.getFloat(mc.playerController);
            }
        } catch (Exception e) {
            curBlockDamageMPField = null;
        }
        return 0;
    }

    private Module getModuleByName(String name) {
        for (Module m : Miau.moduleManager.modules.values()) {
            if (m.getName().equalsIgnoreCase(name)) {
                return m;
            }
        }
        return null;
    }

    private String getRequiredTool(Block block) {
        try {
            String registryName = Block.blockRegistry.getNameForObject(block).toString();
            return REQUIRED_TOOLS.get(registryName);
        } catch (Exception e) {
            return null;
        }
    }

    private int getTotalBlockCount(ItemStack targetStack) {
        if (targetStack == null || !(targetStack.getItem() instanceof ItemBlock)) return 0;
        int total = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() == targetStack.getItem()) {
                if (stack.getMetadata() == targetStack.getMetadata()) {
                    total += stack.stackSize;
                }
            }
        }
        return total;
    }

    private boolean checkPlayerAction() {
        currentAttackTarget = null;
        miningBlockStack = null;

        // 1. Attacking an Entity (KillAura)
        KillAura killAura = (KillAura) Miau.moduleManager.getModule(KillAura.class);
        if (killAura != null && killAura.isEnabled() && killAura.getTarget() != null) {
            currentAttackTarget = killAura.getTarget();
            currentActionText = "Attacking " + currentAttackTarget.getName();
            currentActionProgress = currentAttackTarget.getHealth() / currentAttackTarget.getMaxHealth();
            isScaffolding = false;
            return true;
        }

        // 2. Manual Entity attack (Left click)
        if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY) {
            if (mc.objectMouseOver.entityHit instanceof EntityLivingBase && mc.thePlayer.isSwingInProgress) {
                currentAttackTarget = (EntityLivingBase) mc.objectMouseOver.entityHit;
                currentActionText = "Attacking " + currentAttackTarget.getName();
                currentActionProgress = currentAttackTarget.getHealth() / currentAttackTarget.getMaxHealth();
                isScaffolding = false;
                return true;
            }
        }

        // 3. Scaffold Module
        if (checkScaffoldAction()) {
            return true;
        }
        isScaffolding = false;

        // 4. Mining a Block
        if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            BlockPos pos = mc.objectMouseOver.getBlockPos();
            if (pos != null && mc.theWorld != null) {
                Block block = mc.theWorld.getBlockState(pos).getBlock();
                float damage = getCurBlockDamageMP();
                if (damage > 0) {
                    int meta = block.getMetaFromState(mc.theWorld.getBlockState(pos));
                    miningBlockStack = new ItemStack(block, 1, meta);
                    if (miningBlockStack.getItem() == null) {
                        miningBlockStack = new ItemStack(block);
                    }
                    String requiredTool = getRequiredTool(block);
                    String toolSuffix = requiredTool != null ? " (need " + requiredTool + ")" : "";
                    currentActionText = "Mining " + block.getLocalizedName() + toolSuffix;
                    currentActionProgress = damage;
                    return true;
                }
            }
        }

        // 5. Holding a sword & swinging
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held != null && held.getItem() instanceof ItemSword) {
            boolean isAttacking = mc.gameSettings.keyBindAttack.isKeyDown() || mc.thePlayer.isSwingInProgress;
            if (isAttacking) {
                currentActionText = "Swing Sword";
                currentActionProgress = 1.0f;
                return true;
            }
        }

        // 6. Using other held items
        if (mc.thePlayer.isUsingItem()) {
            if (held != null && !(held.getItem() instanceof ItemBlock)) {
                currentActionText = "Using " + held.getDisplayName();
                currentActionProgress = 1.0f;
                return true;
            }
        }

        return false;
    }

    private boolean checkScaffoldAction() {
        Module scaffold = getModuleByName("Scaffold");
        if (scaffold == null || !scaffold.isEnabled()) {
            isScaffolding = false;
            return false;
        }

        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemBlock)) {
            isScaffolding = false;
            return false;
        }

        scaffoldBlockStack = held;
        scaffoldBlocksRemaining = getTotalBlockCount(held);

        isScaffolding = true;
        currentActionText = "Scaffolding....";
        currentActionProgress = 1.0f;
        return true;
    }

    private void drawDynamicIsland(ScaledResolution sr, float y, float deltaTime) {
        Font font18 = FontRepository.getFont("inter-regular", 18);
        Font font18Bold = FontRepository.getFont("inter-bold", 18);

        String clientNameStr = clientName.getValue();
        String username = getUsername();
        String serverIp = getServerIp();
        String ping = getPing() + "ms";
        String bpsStr = DECIMAL_FORMAT.format(moveBps) + " BPS";

        float logoSize = 16F;
        float dividerWidth = font18.getStringWidth(" | ");

        float textLenClientName = font18.getStringWidth(clientNameStr + " ");
        float textLenName = font18.getStringWidth(username + " ");
        float textLenIP = font18.getStringWidth(serverIp + " ");
        float textLenPing = font18Bold.getStringWidth(ping);
        float textLenBps = font18.getStringWidth(bpsStr);

        float normalWidth = 6F + logoSize + dividerWidth + textLenClientName + dividerWidth + textLenName + textLenIP + textLenPing + dividerWidth + textLenBps + 6F;

        float targetHeadWidth = (currentAttackTarget instanceof AbstractClientPlayer) ? (14F + 6F) : 0f;

        float scaffoldExtrasWidth = 0f;
        if (isScaffolding && scaffoldBlockStack != null) {
            String blockCountStr = String.valueOf(scaffoldBlocksRemaining);
            scaffoldExtrasWidth = 6F + 14F + 6F + 70F + 8F
                    + font18Bold.getStringWidth("Block ") + font18.getStringWidth(blockCountStr) + 12F
                    + font18Bold.getStringWidth("BPS ") + font18.getStringWidth(bpsStr);
        }

        float miningExtrasWidth = 0f;
        if (miningBlockStack != null) {
            String percentStr = (int) (currentActionProgress * 100) + "%";
            miningExtrasWidth = 6F + 14F + 6F + 60F + 6F + font18Bold.getStringWidth(percentStr);
        }

        float actionWidth = 6F + logoSize + dividerWidth + targetHeadWidth + font18.getStringWidth(currentActionText) + scaffoldExtrasWidth + miningExtrasWidth + 6F;

        float targetWidth = normalWidth + (actionWidth - normalWidth) * stateTransition;
        float targetHeight = 20F + (islandState == State.Action ? 1.5F : 0F);

        springWidth = updateSpring(springWidth, targetWidth, springWidthVel, 240f, 0.72f, deltaTime);
        springHeight = updateSpring(springHeight, targetHeight, springHeightVel, 240f, 0.72f, deltaTime);

        float boxWidth = springWidth;
        float boxHeight = springHeight;

        float scaleFactor = scale.getValue() / 100.0f;
        float scaledWidth = sr.getScaledWidth() / scaleFactor;
        float x = (scaledWidth - boxWidth) / 2.0f;

        this.dragging.position.x = x * scaleFactor;
        this.dragging.position.y = y * scaleFactor;
        this.dragging.scale.x = boxWidth * scaleFactor;
        this.dragging.scale.y = boxHeight * scaleFactor;

        GlStateManager.pushMatrix();

        float cornerRadius = boxHeight / 2.0f;

        float borderOffset = 1.0f;
        RoundedUtils.drawRound(
                x - borderOffset,
                y - borderOffset,
                boxWidth + (borderOffset * 2.0f),
                boxHeight + (borderOffset * 2.0f),
                cornerRadius + borderOffset,
                new Color(255, 255, 255, 200)
        );

        RoundedUtils.drawRound(x, y, boxWidth, boxHeight, cornerRadius, new Color(15, 15, 15, 225));

        GlStateManager.popMatrix();

        float currentX = x + 6F;
        drawLogoImage(currentX, y + (boxHeight - logoSize) / 2.0f, logoSize);

        float normalTextAlpha = Math.max(0.0f, 1.0f - stateTransition * 2.2f);
        float actionTextAlpha = Math.max(0.0f, (stateTransition - 0.25f) * 1.33f);

        if (normalTextAlpha > 0.02f) {
            float textX = currentX + logoSize;
            font18.draw(" | ", textX, y + 6F, blendAlpha(new Color(100, 100, 100), normalTextAlpha), false);
            textX += dividerWidth;

            font18.draw(clientNameStr + " ", textX, y + 6F, blendAlpha(getHudAccentColor(), normalTextAlpha), false);
            textX += textLenClientName;

            font18.draw(" | ", textX, y + 6F, blendAlpha(new Color(100, 100, 100), normalTextAlpha), false);
            textX += dividerWidth;

            font18.draw(username + " ", textX, y + 6F, blendAlpha(Color.WHITE, normalTextAlpha), false);
            textX += textLenName;

            font18.draw(serverIp + " ", textX, y + 6F, blendAlpha(new Color(200, 200, 200), normalTextAlpha), false);
            textX += textLenIP;

            font18Bold.draw(ping, textX, y + 6F, blendAlpha(new Color(85, 255, 85), normalTextAlpha), false);
            textX += textLenPing;

            font18.draw(" | ", textX, y + 6F, blendAlpha(new Color(100, 100, 100), normalTextAlpha), false);
            textX += dividerWidth;

            font18.draw(bpsStr, textX, y + 6F, blendAlpha(new Color(255, 185, 80), normalTextAlpha), false);
        }

        if (actionTextAlpha > 0.02f) {
            float textX = currentX + logoSize;
            font18.draw(" | ", textX, y + 6F, blendAlpha(new Color(100, 100, 100), actionTextAlpha), false);
            textX += dividerWidth;

            if (currentAttackTarget instanceof AbstractClientPlayer) {
                drawPlayerHead((AbstractClientPlayer) currentAttackTarget, textX, y + (boxHeight - 14F) / 2f, 14F, actionTextAlpha);
                textX += 14F + 6F;
            }

            font18.draw(currentActionText, textX, y + 6F, blendAlpha(Color.WHITE, actionTextAlpha), false);
            textX += font18.getStringWidth(currentActionText) + 6F;

            if (miningBlockStack != null) {
                drawMiningStats(textX, y, boxHeight, font18Bold, actionTextAlpha);
                String percentStr = (int) (currentActionProgress * 100) + "%";
                textX += 14F + 6F + 60F + 6F + font18Bold.getStringWidth(percentStr) + 6F;
            }

            if (isScaffolding && scaffoldBlockStack != null) {
                drawScaffoldStats(textX, y, boxHeight, font18, font18Bold, actionTextAlpha, bpsStr);
            }
        }
    }

    private void drawPlayerHead(AbstractClientPlayer player, float x, float y, float size, float alpha) {
        try {
            ResourceLocation skin = player.getLocationSkin();
            mc.getTextureManager().bindTexture(skin);
            GlStateManager.pushMatrix();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(770, 771);
            GlStateManager.color(1.0F, 1.0F, 1.0F, alpha);

            Gui.drawScaledCustomSizeModalRect((int) x, (int) y, 8F, 8F, 8, 8, (int) size, (int) size, 64F, 64F);
            Gui.drawScaledCustomSizeModalRect((int) x, (int) y, 40F, 8F, 8, 8, (int) size, (int) size, 64F, 64F);

            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
        } catch (Exception ignored) {}
    }

    // --- Draw block icon + progress bar + percentage while mining ---
    private void drawMiningStats(float x, float y, float boxHeight, Font font18Bold, float alpha) {
        float iconSize = 14F;
        float barWidth = 60F;
        float barHeight = 8F;

        if (miningBlockStack != null) {
            GlStateManager.pushMatrix();
            GlStateManager.color(1f, 1f, 1f, alpha);
            mc.getRenderItem().renderItemIntoGUI(miningBlockStack, (int) x, (int) (y + (boxHeight - iconSize) / 2f));
            GlStateManager.popMatrix();
        }

        float barX = x + iconSize + 6F;
        float barY = y + (boxHeight - barHeight) / 2f;
        RoundedUtils.drawRound(barX, barY, barWidth, barHeight, 2F, blendColor(new Color(60, 60, 60), alpha));

        float fillPercent = Math.max(0f, Math.min(1f, currentActionProgress));
        if (fillPercent > 0.01f) {
            RoundedUtils.drawRound(barX, barY, barWidth * fillPercent, barHeight, 2F, blendColor(getHudAccentColor(), alpha));
        }

        float textX = barX + barWidth + 6F;
        String percentStr = (int) (fillPercent * 100) + "%";
        font18Bold.draw(percentStr, textX, y + 6F, blendAlpha(new Color(85, 255, 85), alpha), false);
    }

    private void drawScaffoldStats(float x, float y, float boxHeight, Font font18, Font font18Bold, float alpha, String currentBpsStr) {
        float iconSize = 14F;
        float barWidth = 70F;
        float barHeight = 8F;

        GlStateManager.pushMatrix();
        GlStateManager.color(1f, 1f, 1f, alpha);
        mc.getRenderItem().renderItemIntoGUI(scaffoldBlockStack, (int) x, (int) (y + (boxHeight - iconSize) / 2f));
        GlStateManager.popMatrix();

        float barX = x + iconSize + 6F;
        float barY = y + (boxHeight - barHeight) / 2f;
        RoundedUtils.drawRound(barX, barY, barWidth, barHeight, 2F, blendColor(new Color(60, 60, 60), alpha));

        int maxStack = 64;
        float fillPercent = Math.max(0f, Math.min(1f, scaffoldBlocksRemaining / (float) maxStack));
        if (fillPercent > 0.01f) {
            RoundedUtils.drawRound(barX, barY, barWidth * fillPercent, barHeight, 2F, blendColor(getHudAccentColor(), alpha));
        }

        float statsX = barX + barWidth + 8F;
        font18Bold.draw("Block ", statsX, y + 6F, blendAlpha(new Color(255, 140, 0), alpha), false);
        statsX += font18Bold.getStringWidth("Block ");

        String blockCountStr = String.valueOf(scaffoldBlocksRemaining);
        font18.draw(blockCountStr, statsX, y + 6F, blendAlpha(new Color(85, 255, 85), alpha), false);
        statsX += font18.getStringWidth(blockCountStr) + 12F;

        font18Bold.draw("BPS ", statsX, y + 6F, blendAlpha(new Color(255, 235, 60), alpha), false);
        statsX += font18Bold.getStringWidth("BPS ");

        font18.draw(currentBpsStr, statsX, y + 6F, blendAlpha(new Color(255, 105, 180), alpha), false);
    }

    private Color getHudAccentColor() {
        if (this.color.getValue() == 0) {
            try {
                HUD hud = (HUD) Miau.moduleManager.modules.get(HUD.class);
                if (hud != null) {
                    return hud.getColor(System.currentTimeMillis());
                }
            } catch (Exception ignored) {}
            return new Color(59, 155, 240);
        } else {
            try {
                Object val = customColor.getValue();
                if (val instanceof Color) {
                    return (Color) val;
                } else if (val instanceof Number) {
                    return new Color(((Number) val).intValue(), true);
                }
            } catch (Exception ignored) {}
            return new Color(59, 155, 240);
        }
    }

    private void drawLogoImage(float x, float y, float size) {
        try {
            GlStateManager.pushMatrix();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(770, 771);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

            mc.getTextureManager().bindTexture(LOGO_IMAGE);
            Gui.drawModalRectWithCustomSizedTexture((int)x, (int)y, 0, 0, (int)size, (int)size, (int)size, (int)size);

            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
        } catch (Exception e) {
            Font font18Bold = FontRepository.getFont("inter-bold", 18);
            font18Bold.draw(clientName.getValue(), x, y + 5F, Color.WHITE.getRGB(), false);
        }
    }

    private int blendAlpha(Color baseColor, float alphaFactor) {
        int alpha = Math.max(0, Math.min(255, (int)(baseColor.getAlpha() * alphaFactor)));
        return new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), alpha).getRGB();
    }

    private Color blendColor(Color baseColor, float alphaFactor) {
        int alpha = Math.max(0, Math.min(255, (int)(baseColor.getAlpha() * alphaFactor)));
        return new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), alpha);
    }

    private int getPing() {
        if (mc.thePlayer == null || mc.getNetHandler() == null) return 0;
        net.minecraft.client.network.NetworkPlayerInfo playerInfo = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
        return playerInfo != null ? playerInfo.getResponseTime() : 0;
    }

    private String getServerIp() {
        if (mc.theWorld == null || mc.isSingleplayer()) return "SinglePlayer";
        return mc.getCurrentServerData() != null ? mc.getCurrentServerData().serverIP : "Unknown";
    }
}