package primevisuals;

import net.minecraft.item.Item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * One row in the left list. "paths" are texture paths relative to assets/minecraft/textures/
 * without ".png". A path ending with * is a prefix wildcard.
 */
public record Category(String id, String name, Item icon, boolean extra, String[] paths) {

    public List<String> match(Set<String> textures) {
        List<String> out = new ArrayList<>();
        for (String p : paths) {
            if (p.endsWith("*")) {
                String pre = p.substring(0, p.length() - 1);
                List<String> found = new ArrayList<>();
                for (String s : textures) {
                    if (s.startsWith(pre)) found.add(s);
                }
                Collections.sort(found);
                for (String s : found) {
                    if (!out.contains(s)) out.add(s);
                }
            } else {
                String s = p + ".png";
                if (textures.contains(s) && !out.contains(s)) out.add(s);
            }
        }
        return out;
    }
}
