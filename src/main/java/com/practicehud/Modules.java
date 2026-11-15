package com.practicehud;

import com.practicehud.Hud.Row;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Registry of every module plus the code that implements them. */
public final class Modules {
    public static final String CAT_CRYSTAL = "Crystal & Respawn";
    public static final String CAT_COMBAT = "Combat";
    public static final String CAT_MACE = "Mace & Wind";
    public static final String CAT_CART = "Minecart & Redstone";
    public static final String CAT_UTIL = "Utility";
    public static final String CAT_VISUAL = "Visuals";

    public static final List<String> CATEGORIES = List.of(CAT_CRYSTAL, CAT_COMBAT, CAT_MACE, CAT_CART, CAT_UTIL, CAT_VISUAL);
    public static final List<Module> ALL = new ArrayList<>();

    // Smoothed minecart velocities (blocks per tick), measured from client positions.
    private static final Map<Integer, Vec3d> LAST_CART_POS = new HashMap<>();
    private static final Map<Integer, Vec3d> CART_VEL = new HashMap<>();

    private Modules() {
    }

    private static void add(String id, String name, String cat, String desc, Module.Renderer r) {
        ALL.add(new Module(id, name, cat, desc, r));
    }

    public static Module byId(String id) {
        for (Module m : ALL) if (m.id.equals(id)) return m;
        return null;
    }

    public static boolean enabled(String id) {
        Module m = byId(id);
        return m != null && m.isEnabled();
    }

    static {
        // ---- Crystal & Respawn ----
        add("totem_status", "Totem Status", CAT_CRYSTAL,
                "Totem count and whether your offhand holds one.", Modules::totemStatus);
        add("anchor_meter", "Respawn Anchor Meter", CAT_CRYSTAL,
                "Shows charge level and blast size of the respawn anchor you look at.", Modules::anchorMeter);

        // ---- Combat ----
        add("reticle", "Custom Crosshair", CAT_COMBAT,
                "Replaces the crosshair (shape/size/colour in settings). Changes colour over a target in reach.", null);
        add("target_ring", "Target Tracking Ring", CAT_COMBAT,
                "A static ring at screen centre for aim practice.", Modules::targetRing);
        add("distance", "Combat Distance Counter", CAT_COMBAT,
                "Distance in blocks to the mob or player under your crosshair (up to 64).", Modules::distanceCounter);
        add("sprint", "Sprint Indicator", CAT_COMBAT,
                "Shows sprint / sneak state and horizontal speed.", Modules::sprintIndicator);
        add("guard_timing", "Guard Timing Indicator", CAT_COMBAT,
                "Weapon attack cooldown and shield-disabled state.", Modules::guardTiming);
        add("shield_status", "Shield Status", CAT_COMBAT,
                "Shield durability, blocking state and cooldown.", Modules::shieldStatus);
        add("ability_cooldown", "Ability Cooldown Bar", CAT_COMBAT,
                "Cooldown progress of the item in your hand (pearls, wind charges, shields...).", Modules::abilityCooldown);
        add("netherite_hud", "Netherite Combat HUD", CAT_COMBAT,
                "Health, potion effects and armour durability in one panel.", Modules::combatHud);
        add("entity_info", "Entity Information", CAT_COMBAT,
                "Name, health bar and effects of the living entity under your crosshair.", Modules::entityInfo);

        // ---- Mace & Wind ----
        add("crit_bar", "Critical Hit Timing Bar", CAT_MACE,
                "Shows whether a hit would crit right now, plus fall distance and attack charge.", Modules::critBar);
        add("mace_height", "Mace Height Calculator", CAT_MACE,
                "Fall distance, the 1.5-block smash minimum and estimated bonus damage.", Modules::maceHeight);
        add("wind_trainer", "Wind Burst Combo Trainer", CAT_MACE,
                "Step prompts for wind charge -> mace combos.", Modules::windTrainer);
        add("jump_gauge", "Jump Velocity Gauge", CAT_MACE,
                "Vertical speed in blocks/second.", Modules::jumpGauge);
        add("elytra", "Elytra HUD", CAT_MACE,
                "Pitch, speed and elytra durability while gliding.", Modules::elytraHud);

        // ---- Minecart & Redstone ----
        add("cart_speed", "Minecart Speedometer", CAT_CART,
                "Speed and heading of the three nearest minecarts.", Modules::cartSpeedometer);
        add("rail_counter", "Inventory Rail Counter", CAT_CART,
                "Rails, minecarts and redstone in your inventory.", Modules::railCounter);

        // ---- Utility ----
        add("throw_calc", "Throw Landing Calculator", CAT_UTIL,
                "Predicts where an ender pearl, snowball, egg or splash potion will land from your aim.", Modules::throwCalc);
        add("reach", "Interaction Distance", CAT_UTIL,
                "Distance to the block or entity you are targeting.", Modules::reachReadout);
    }

    // =====================================================================
    // Frame / tick entry points
    // =====================================================================

    public static void renderAll(DrawContext ctx, MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.options.hudHidden) return;
        Hud.begin();
        for (Module m : ALL) {
            if (m.isEnabled()) m.render(ctx, mc, p);
        }
    }

    public static void tick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) {
            LAST_CART_POS.clear();
            CART_VEL.clear();
            return;
        }
        if (!enabled("cart_speed")) return;
        Map<Integer, Vec3d> now = new HashMap<>();
        for (AbstractMinecartEntity c : mc.world.getEntitiesByClass(AbstractMinecartEntity.class,
                mc.player.getBoundingBox().expand(48), e -> true)) {
            Vec3d pos = new Vec3d(c.getX(), c.getY(), c.getZ());
            now.put(c.getId(), pos);
            Vec3d prev = LAST_CART_POS.get(c.getId());
            if (prev != null) {
                Vec3d v = pos.subtract(prev);
                Vec3d old = CART_VEL.getOrDefault(c.getId(), v);
                CART_VEL.put(c.getId(), old.multiply(0.6).add(v.multiply(0.4)));
            }
        }
        LAST_CART_POS.clear();
        LAST_CART_POS.putAll(now);
        CART_VEL.keySet().retainAll(now.keySet());
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private static int count(ClientPlayerEntity p, Item... items) {
        int n = 0;
        for (int i = 0; i < p.getInventory().size(); i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isEmpty()) continue;
            for (Item it : items) {
                if (s.isOf(it)) {
                    n += s.getCount();
                    break;
                }
            }
        }
        return n;
    }

    private static float durabilityFrac(ItemStack s) {
        if (s.isEmpty() || s.getMaxDamage() <= 0) return 1f;
        return (s.getMaxDamage() - s.getDamage()) / (float) s.getMaxDamage();
    }

    private static int fracColor(float f) {
        return f > 0.5f ? Hud.GREEN : f > 0.2f ? Hud.YELLOW : Hud.RED;
    }

    private static double hSpeed(ClientPlayerEntity p) {
        Vec3d v = p.getVelocity();
        return Math.sqrt(v.x * v.x + v.z * v.z) * 20.0;
    }

    /** Nearest living entity along the camera ray, ignoring walls. Returns null if none. */
    private static LivingEntity lookedAt(MinecraftClient mc, double range) {
        if (mc.getCameraEntity() == null || mc.world == null) return null;
        var cam = mc.getCameraEntity();
        Vec3d start = cam.getCameraPosVec(1f);
        Vec3d dir = cam.getRotationVec(1f);
        Vec3d end = start.add(dir.multiply(range));
        LivingEntity best = null;
        double bestD = range * range;
        for (LivingEntity t : mc.world.getEntitiesByClass(LivingEntity.class,
                cam.getBoundingBox().stretch(dir.multiply(range)).expand(1.0),
                e -> e != cam && e.isAlive() && !e.isSpectator())) {
            Optional<Vec3d> hit = t.getBoundingBox().expand(t.getTargetingMargin()).raycast(start, end);
            if (hit.isPresent()) {
                double d = start.squaredDistanceTo(hit.get());
                if (d < bestD) {
                    bestD = d;
                    best = t;
                }
            }
        }
        return best;
    }

    private static String effectName(StatusEffectInstance e) {
        String n = e.getEffectType().value().getName().getString();
        return e.getAmplifier() > 0 ? n + " " + (e.getAmplifier() + 1) : n;
    }

    // =====================================================================
    // Crystal & Respawn
    // =====================================================================

    private static void totemStatus(DrawContext ctx, MinecraftClient mc, ClientPlayerEntity p) {
        int n = count(p, Items.TOTEM_OF_UNDYING);
        boolean off = p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
        Hud.panel(ctx, "Totems", n > 0 ? Hud.GOLD : Hud.RED,
                Row.of("Count: " + n, n > 1 ? Hud.GREEN : n == 1 ? Hud.YELLOW : Hud.RED),
                Row.of("Offhand: " + (off ? "ready" : "empty"), off ? Hud.GREEN : Hud.GRAY));
    }

    private static void anchorMeter(DrawContext ctx, MinecraftClient mc, ClientPlayerEntity p) {
        if (!(mc.crosshairTarget instanceof BlockHitResult bhr) || bhr.getType() != HitResult.Type.BLOCK) return;
        BlockState st = mc.world.getBlockState(bhr.getBlockPos());
        if (!st.isOf(Blocks.RESPAWN_ANCHOR)) return;
        int charges = st.get(RespawnAnchorBlock.CHARGES);
        Hud.panel(ctx, "Respawn Anchor", Hud.AQUA,
                Row.bar("Charges: " + charges + "/4", charges / 4f, charges > 0 ? Hud.AQUA : Hud.RED),
                Row.of("Blast power: 5.0 (outside the Nether)", Hud.GRAY),
                Row.of("Damage radius: ~10 blocks", Hud.GRAY));
    }

    // =====================================================================
    // Combat
    // =====================================================================

    public static void drawCrosshair(DrawContext ctx, MinecraftClient mc) {
        Config c = Config.get();
        int cx = ctx.getScaledWindowWidth() / 2;
        int cy = ctx.getScaledWindowHeight() / 2;
        int color = mc.targetedEntity != null ? c.crosshairTargetColor : c.crosshairColor;
        int extra = 0;
        if (c.crosshairDynamic && mc.player != null) {
            Vec3d v = mc.player.getVelocity();
            extra = (int) Math.round(Math.sqrt(v.x * v.x + v.z * v.z) * 12.0);
            if (!mc.player.isOnGround()) extra += 2;
        }
        int s = c.crosshairSize;
        int g = c.crosshairGap + extra;
        int t = Math.max(1, c.crosshairThickness);
        int lo = -t / 2;
        int hi = lo + t;

        if (c.crosshairShape == 0 || c.crosshairShape == 3) {
            ctx.fill(cx - g - s, cy + lo, cx - g, cy + hi, color);
            ctx.fill(cx + g, cy + lo, cx + g + s, cy + hi, color);
            ctx.fill(cx + lo, cy - g - s, cx + hi, cy - g, color);
            ctx.fill(cx + lo, cy + g, cx + hi, cy + g + s, color);
        }
        if (c.crosshairShape == 1 || c.crosshairShape == 3) {
            int r = Math.max(1, t);
            ctx.fill(cx - r, cy - r, cx + r, cy + r, color);
        }
        if (c.crosshairShape == 2) {
            drawCircle(ctx, cx, cy, s + g, t, color);
        }
    }

    private static void drawCircle(DrawContext ctx, int cx, int cy, int radius, int thickness, int color) {
        int steps = Math.max(48, radius * 6);
        int lo = -thickness / 2;
        for (int i = 0; i < steps; i++) {
            double a = i * (Math.PI * 2.0 / steps);
            int x = cx + (int) Math.round(Math.cos(a) * radius);
            int y = cy + (int) Math.round(Math.sin(a) * radius);
            ctx.fill(x + lo, y + lo, x + lo + thickness, y + lo + thickness, color);
        }
    }

    private static void targetRing(DrawContext ctx, MinecraftClient mc, ClientPlayerEntity p) {
        Config c = Config.get();
        drawCircle(ctx, ctx.getScaledWindowWidth() / 2, ctx.getScaledWindowHeight() / 2, c.ringRadius, 1, c.ringColor);
    }

    private static void distanceCounter(DrawContext ctx, MinecraftClient mc, ClientPlayerEntity p) {
        LivingEntity t = lookedAt(mc, 64);
        if (t == null) return;
        double d = p.getEyePos().distanceTo(t.getBoundingBox().getCenter());
        int cx = ctx.getScaledWindowWidth() / 2;
        int cy = ctx.getScaledWindowHeight() / 2;
        ctx.drawTextWithShadow(mc.textRenderer, String.format("%.1f m", d), cx + 12, cy - 4,
                d <= 3.0 ? Hud.GREEN : d <= 6.0 ? Hud.YELLOW : Hud.WHITE);
    }

    private static void sprintIndicator(DrawContext ctx, MinecraftClient mc, ClientPlayerEntity p) {
        String state = p.isSprinting() ? "SPRINTING" : p.isSneaking() ? "Sneaking" : "Walking";
        int color = p.isSprinting() ? Hud.GREEN : p.isSneaking() ? Hud.YELLOW : Hud.GRAY;
        Hud.panel(ctx, "Movement", color,
                Row.of(state, color),
                Row.of(String.format("Speed: %.2f b/s", hSpeed(p)), Hud.WHITE));
    }

    private static void guardTiming(DrawContext ctx, MinecraftClient mc, ClientPlayerEntity p) {
        float atk = p.getAttackCooldownProgress(0f);
        List<Row> rows = new ArrayList<>();
        rows.add(Row.bar(atk >= 1f ? "Weapon: READY" : String.format("Weapon: %d%%", Math.round(atk * 100)),
                atk, atk >= 0.9f ? Hud.GREEN : Hud.YELLOW));
        ItemStack shield = shieldStack(p);
        if (shield != null) {
            float cd = p.getItemCooldownManager().getCooldownProgress(shield, 0f);
            rows.add(cd > 0f
                    ? Row.bar(String.format("Shield DISABLED %d%%", Math.round(cd * 100)), cd, Hud.RED)
                    : Row.of("Shield: ready", Hud.GREEN));
        }
        Hud.panel(ctx, "Guard Timing", Hud.AQUA, rows.toArray(new Row[0]));
    }

    private static ItemStack shieldStack(ClientPlayerEntity p) {
        if (p.getMainHandStack().isOf(Items.SHIELD)) return p.getMainHandStack();
        if (p.getOffHandStack().isOf(Items.SHIELD)) return p.getOffHandStack();
        return null;
    }

    private static void shieldStatus(DrawContext ctx, MinecraftClient mc, ClientPlayerEntity p) {
        ItemStack s = shieldStack(p);
        if (s == null) return;
        float f = durabilityFrac(s);
        float cd = p.getItemCooldownManager().getCooldownProgress(s, 0f);
        Hud.panel(ctx, "Shield", Hud.AQUA,
                Row.bar(String.format("Durability: %d/%d", s.getMaxDamage() - s.getDamage(), s.getMaxDamage()), f, fracColor(f)),
                Row.of(cd > 0f ? "DISABLED" : p.isBlocking() ? "Blocking" : "Ready", cd > 0f ? Hud.RED : p.isBlocking() ? Hud.GREEN : Hud.GRAY));
    }

    private static void abilityCooldown(DrawContext ctx, MinecraftClient mc, ClientPlayerEntity p) {
        ItemStack s = p.getMainHandStack();
        if (s.isEmpty()) return;
        float cd = p.getItemCooldownManager().getCooldownProgress(s, 0f);
        if (cd <= 0f) return;
        Hud.panel(ctx, "Cooldown", Hud.GOLD,
                Row.bar(s.getName().getString() + " " + Math.round(cd * 100) + "%", cd, Hud.GOLD));
    }

    private static void combatHud(DrawContext ctx, MinecraftClient mc, ClientPlayerEntity p) {
        List<Row> rows = new ArrayList<>();
        float hp = p.getHealth();
        float max = p.getMaxHealth();
        String hpText = String.format("Health: %.1f/%.1f", hp, max);
        if (p.getAbsorptionAmount() > 0) hpText += String.format(" (+%.1f)", p.getAbsorptionAmount());
        rows.add(Row.bar(hpText, hp / max, fracColor(hp / max)));
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        String[] names = {"Helmet", "Chest", "Legs", "Boots"};
        for (int i = 0; i < slots.length; i++) {
            ItemStack a = p.getEquippedStack(slots[i]);
            if (a.isEmpty()) continue;
            float f = durabilityFrac(a);
            rows.add(Row.bar(names[i] + ": " + Math.round(f * 100) + "%", f, fracColor(f)));
        }
        int shown = 0;
        for (StatusEffectInstance e : p.getStatusEffects()) {
            if (shown++ >= 6) break;
            rows.add(Row.of(effectName(e) + " " + Hud.time(e.getDuration()), Hud.WHITE));
        }
        Hud.panel(ctx, "Combat", Hud.RED, rows.toArray(new Row[0]));
    }

    private static void entityInfo(DrawContext ctx, MinecraftClient mc, ClientPlayerEntity p) {
        LivingEntity t = lookedAt(mc, 32);
        if (t == null) return;
        int cx = ctx.getScaledWindowWidth() / 2;
        int y = ctx.getScaledWindowHeight() / 2 + 18;
        Hud.centered(ctx, t.getDisplayName().getString(), cx, y, Hud.WHITE);
        float f = Math.max(0f, Math.min(1f, t.getHealth() / t.getMaxHealth()));
        int bw = 60;
        ctx.fill(cx - bw / 2, y + 11, cx + bw / 2, y + 14, 0xA0000000);
        ctx.fill(cx - bw / 2, y + 11, cx - bw / 2 + Math.round(bw * f), y + 14, fracColor(f));
        Hud.centered(ctx, String.format("%.1f / %.1f", t.getHealth(), t.getMaxHealth()), cx, y + 16, Hud.GRAY);
        int ey = y + 27;
        int shown = 0;
        for (StatusEffectInstance e : t.getStatusEffects()) {
            if (shown++ >= 4) break;
            Hud.centered(ctx, effectName(e) + " " + Hud.time(e.getDuration()), cx, ey, Hud.AQUA);
            ey += 10;
        }
    }

    // =====================================================================
    // Mace & Wind
    // =====================================================================

    private static boolean critReady(ClientPlayerEntity p) {
        return p.fallDistance > 0.0f && !p.isOnGround() && !p.isClimbing() && !p.isTouchingWater()
                && !p.hasStatusEffect(StatusEffects.BLINDNESS) && !p.hasVehicle()
                && p.getAttackCooldownProgress(0.5f) > 0.9f && !p.isSprinting();
    }

    private static void critBar(DrawContext ctx, MinecraftClient mc, ClientPlayerEntity p) {
        boolean ready = critReady(p);
        float atk = p.getAttackCooldownProgress(0f);
        Hud.panel(ctx, "Critical Hit", ready ? Hud.GREEN : Hud.GRAY,
                Row.of(ready ? "CRIT READY" : "No crit", ready ? Hud.GREEN : Hud.RED),
                Row.bar(String.format("Attack charge %d%%", Math.round(atk * 100)), atk, atk >= 0.9f ? Hud.GREEN : Hud.YELLOW),
                Row.of(String.format("Fall distance: %.2f", (double) p.fallDistance), Hud.WHITE),
                Row.of("Tip: don't sprint-hit; fall and swing at full charge", Hud.GRAY));
    }

    /** Bonus damage a mace adds for a given fall distance (before armour). */
    private static double maceBonus(double fall) {
        if (fall <= 1.5) return 0;
        if (fall <= 3.0) return 4.0 * fall;
        if (fall <= 8.0) return 12.0 + 2.0 * (fall - 3.0);
        return 22.0 + (fall - 8.0);
    }

    private static void maceHeight(DrawContext ctx, MinecraftClient mc, ClientPlayerEntity p) {
        boolean holding = p.getMainHandStack().isOf(Items.MACE);
        double fall = p.fallDistance;
        if (!holding && fall <= 1.5) return;
        boolean smash = fall > 1.5;
        Hud.panel(ctx, "Mace", Hud.GOLD,
                Row.bar(String.format("Fall: %.1f blocks (min 1.5)", fall), (float) (fall / 1.5), smash ? Hud.GREEN : Hud.YELLOW),
                Row.of(smash ? String.format("Bonus damage: +%.1f (%.1f hearts)", maceBonus(fall), maceBonus(fall) / 2.0) : "Smash attack not armed", smash ? Hud.GREEN : Hud.GRAY),
                Row.of(holding ? "Mace in hand" : "Mace not held", holding ? Hud.GREEN : Hud.RED));
    }

    private static void windTrainer(DrawContext ctx, MinecraftClient mc, ClientPlayerEntity p) {
        boolean wind = p.getMainHandStack().isOf(Items.WIND_CHARGE) || p.getOffHandStack().isOf(Items.WIND_CHARGE);
        boolean mace = p.getMainHandStack().isOf(Items.MACE);
        double vy = p.getVelocity().y;
        String step;
        int color;
        if (p.isOnGround() && !mace && !wind) {
            step = "1. Hold a wind charge";
            color = Hud.GRAY;
        } else if (p.isOnGround() && wind) {
            step = "2. Look down + right-click to launch";
            color = Hud.YELLOW;
        } else if (!p.isOnGround() && vy > 0.1 && !mace) {
            step = "3. Switch to the mace";
            color = Hud.AQUA;
        } else if (!p.isOnGround() && mace && p.fallDistance <= 1.5) {
            step = "4. Wait for the fall...";
            color = Hud.YELLOW;
        } else if (!p.isOnGround() && mace) {
            step = "5. ATTACK NOW!";
            color = Hud.GREEN;
        } else {
            step = "Ready";
            color = Hud.GRAY;
        }
        Hud.panel(ctx, "Wind Burst Trainer", color, Row.of(step, color));
    }

    private static void jumpGauge(DrawContext ctx, MinecraftClient mc, ClientPlayerEntity p) {
        double vy = p.getVelocity().y * 20.0;
        float frac = (float) Math.min(1.0, Math.abs(vy) / 30.0);
        Hud.panel(ctx, "Vertical Speed", vy >= 0 ? Hud.GREEN : Hud.RED,
                Row.bar(String.format("%+.1f b/s", vy), frac, vy >= 0 ? Hud.GREEN : Hud.RED));
    }

    private static void elytraHud(DrawContext ctx, MinecraftClient mc, ClientPlayerEntity p) {
        if (!p.isGliding()) return;
        ItemStack chest = p.getEquippedStack(EquipmentSlot.CHEST);
        double speed = p.getVelocity().length() * 20.0;
        List<Row> rows = new ArrayList<>();
        rows.add(Row.of(String.format("Pitch: %.1f°", p.getPitch()), Hud.WHITE));
        rows.add(Row.of(String.format("Speed: %.1f b/s (%.0f km/h)", speed, speed * 3.6), Hud.AQUA));
        if (chest.isOf(Items.ELYTRA)) {
            float f = durabilityFrac(chest);
            rows.add(Row.bar(String.format("Durability: %d/%d", chest.getMaxDamage() - chest.getDamage(), chest.getMaxDamage()), f, fracColor(f)));
        }
        Hud.panel(ctx, "Elytra", Hud.AQUA, rows.toArray(new Row[0]));
    }

    // =====================================================================
    // Minecart & Redstone
    // =====================================================================

    private static void cartSpeedometer(DrawContext ctx, MinecraftClient mc, ClientPlayerEntity p) {
        List<AbstractMinecartEntity> carts = new ArrayList<>(mc.world.getEntitiesByClass(AbstractMinecartEntity.class,
                p.getBoundingBox().expand(32), e -> true));
        if (carts.isEmpty()) return;
        carts.sort(Comparator.comparingDouble(c -> c.squaredDistanceTo(p)));
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < Math.min(3, carts.size()); i++) {
            AbstractMinecartEntity c = carts.get(i);
            Vec3d v = CART_VEL.getOrDefault(c.getId(), Vec3d.ZERO);
            double bps = v.length() * 20.0;
            String dir = bps < 0.05 ? "still" : Direction.getFacing(v.x, v.y, v.z).asString();
            rows.add(Row.of(String.format("%s: %.1f b/s %s (%.0fm)", c.getType().getName().getString(), bps, dir,
                    (double) c.distanceTo(p)), bps > 8 ? Hud.GREEN : Hud.WHITE));
        }
        Hud.panel(ctx, "Minecarts", Hud.GOLD, rows.toArray(new Row[0]));
    }

    private static void railCounter(DrawContext ctx, MinecraftClient mc, ClientPlayerEntity p) {
        int rails = count(p, Items.RAIL, Items.POWERED_RAIL, Items.DETECTOR_RAIL, Items.ACTIVATOR_RAIL);
        int carts = count(p, Items.MINECART, Items.CHEST_MINECART, Items.FURNACE_MINECART, Items.TNT_MINECART,
                Items.HOPPER_MINECART);
        int redstone = count(p, Items.REDSTONE);
        Hud.panel(ctx, "Rail Supplies", Hud.GOLD,
                Row.of("Rails: " + rails, rails > 0 ? Hud.WHITE : Hud.RED),
                Row.of("Minecarts: " + carts, carts > 0 ? Hud.WHITE : Hud.RED),
                Row.of("Redstone: " + redstone, redstone > 0 ? Hud.WHITE : Hud.RED));
    }

    // =====================================================================
    // Utility
    // =====================================================================

    private static boolean throwable(ItemStack s) {
        return s.isOf(Items.ENDER_PEARL) || s.isOf(Items.SNOWBALL) || s.isOf(Items.EGG)
                || s.isOf(Items.SPLASH_POTION) || s.isOf(Items.LINGERING_POTION);
    }

    private static void throwCalc(DrawContext ctx, MinecraftClient mc, ClientPlayerEntity p) {
        ItemStack held = throwable(p.getMainHandStack()) ? p.getMainHandStack()
                : throwable(p.getOffHandStack()) ? p.getOffHandStack() : null;
        if (held == null) return;
        boolean potion = held.isOf(Items.SPLASH_POTION) || held.isOf(Items.LINGERING_POTION);
        double speed = potion ? 0.5 : 1.5;
        double gravity = potion ? 0.05 : 0.03;
        float pitch = p.getPitch() + (potion ? -20f : 0f);

        Vec3d vel = Vec3d.fromPolar(pitch, p.getYaw()).multiply(speed);
        Vec3d pv = p.getVelocity();
        vel = vel.add(pv.x, p.isOnGround() ? 0.0 : pv.y, pv.z);
        Vec3d pos = new Vec3d(p.getX(), p.getEyeY() - 0.1, p.getZ());
        Vec3d start = pos;

        Vec3d landing = null;
        int ticks = 0;
        for (int i = 0; i < 300; i++) {
            Vec3d next = pos.add(vel);
            BlockHitResult hit = mc.world.raycast(new RaycastContext(pos, next,
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
            ticks++;
            if (hit.getType() != HitResult.Type.MISS) {
                landing = hit.getPos();
                break;
            }
            pos = next;
            vel = vel.multiply(0.99).subtract(0, gravity, 0);
            if (pos.y < mc.world.getBottomY() - 16) break;
        }

        if (landing == null) {
            Hud.panel(ctx, "Throw Landing", Hud.GRAY, Row.of("No landing found", Hud.RED));
            return;
        }
        double horiz = Math.hypot(landing.x - start.x, landing.z - start.z);
        Hud.panel(ctx, "Throw Landing", Hud.AQUA,
                Row.of(String.format("Lands at %.0f, %.0f, %.0f", landing.x, landing.y, landing.z), Hud.WHITE),
                Row.of(String.format("Distance: %.1f blocks", horiz), Hud.GREEN),
                Row.of(String.format("Flight time: %d ticks (%.1fs)", ticks, ticks / 20.0), Hud.GRAY));
    }

    private static void reachReadout(DrawContext ctx, MinecraftClient mc, ClientPlayerEntity p) {
        HitResult hr = mc.crosshairTarget;
        if (hr == null || hr.getType() == HitResult.Type.MISS) return;
        double d = p.getEyePos().distanceTo(hr.getPos());
        int cx = ctx.getScaledWindowWidth() / 2;
        int cy = ctx.getScaledWindowHeight() / 2;
        Hud.centered(ctx, String.format("%.2f", d), cx, cy + 12, d <= 3.0 ? Hud.GREEN : d <= 4.5 ? Hud.YELLOW : Hud.RED);
    }
}
