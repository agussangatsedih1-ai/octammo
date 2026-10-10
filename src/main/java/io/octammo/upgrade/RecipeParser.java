package io.octammo.upgrade;

import io.octammo.core.ItemRef;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses "MMOITEM:T:ID + MONEY:100 + ... => MMOITEM:T:ID2 (75%)". */
public final class RecipeParser {
    private static final Pattern CHANCE = Pattern.compile("\\(\\s*([0-9]+(?:\\.[0-9]+)?)\\s*%?\\s*\\)\\s*$");

    private RecipeParser() {}

    public record Parsed(ItemRef input, List<Cost> costs, ItemRef output, double chance) {}

    public static Parsed parse(String line) {
        String[] sides = line.split("=>", 2);
        if (sides.length != 2) throw new IllegalArgumentException("missing '=>'");

        String[] left = sides[0].trim().split("\\s*\\+\\s*");
        ItemRef input = parseItem(left[0], "input");

        List<Cost> costs = new ArrayList<>();
        for (int i = 1; i < left.length; i++) costs.add(cost(left[i]));

        String right = sides[1].trim();
        double chance = 100;
        Matcher m = CHANCE.matcher(right);
        if (m.find()) {
            chance = Math.max(0, Math.min(100, Double.parseDouble(m.group(1))));
            right = right.substring(0, m.start()).trim();
        }
        ItemRef output = parseItem(right, "output");
        return new Parsed(input, costs, output, chance);
    }

    public static ItemRef parseItem(String token, String what) {
        String[] p = token.trim().split(":");
        if (p.length < 3 || p.length > 4 || !p[0].equalsIgnoreCase("MMOITEM")) {
            throw new IllegalArgumentException("the " + what + " must look like MMOITEM:<TYPE>:<ID>[:amount], got '" + token.trim() + "'");
        }
        return new ItemRef(p[1], p[2], p.length == 4 ? Integer.parseInt(p[3].trim()) : 1);
    }

    private static Cost cost(String token) {
        String[] p = token.trim().split(":");
        String kind = p[0].toUpperCase(Locale.ROOT);
        switch (kind) {
            case "MONEY" -> {
                need(p, 2, 2, token);
                double amount = Double.parseDouble(p[1].trim());
                if (amount < 0) throw new IllegalArgumentException("negative money in '" + token.trim() + "'");
                return new Cost.Money(amount);
            }
            case "LEVELS", "LEVEL" -> {
                need(p, 2, 2, token);
                return new Cost.Levels(Math.max(0, Integer.parseInt(p[1].trim())));
            }
            case "MMOITEM", "MMO" -> {
                need(p, 3, 4, token);
                return new Cost.MmoItem(new ItemRef(p[1], p[2], p.length == 4 ? Integer.parseInt(p[3].trim()) : 1));
            }
            case "ITEM" -> {
                need(p, 2, 3, token);
                Material mat = Material.matchMaterial(p[1].trim());
                if (mat == null || mat.isAir()) throw new IllegalArgumentException("unknown material '" + p[1].trim() + "'");
                return new Cost.Vanilla(mat, p.length == 3 ? Math.max(1, Integer.parseInt(p[2].trim())) : 1);
            }
            default -> throw new IllegalArgumentException("unknown cost '" + token.trim() + "' (use MONEY, LEVELS, MMOITEM or ITEM)");
        }
    }

    private static void need(String[] parts, int min, int max, String token) {
        if (parts.length < min || parts.length > max) throw new IllegalArgumentException("bad cost '" + token.trim() + "'");
    }
}
