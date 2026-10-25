package com.vindhyanstudio.catalogue;

/**
 * Which inputs a category asks the customer for, written in the Excel "categories" sheet, e.g.
 * {@code photo:1, size} or {@code photo:2-3, notes} or {@code text?, colour}.
 * {@code photo:N} / {@code photo:MIN-MAX} = number of photos; a trailing {@code ?} makes text optional.
 */
public record CustomisationSpec(int minPhotos, int maxPhotos, boolean text, boolean textRequired,
                                boolean colour, boolean size, boolean notes) {

    public static final CustomisationSpec NONE = new CustomisationSpec(0, 0, false, false, false, false, false);

    public boolean madeToOrder() {
        return maxPhotos > 0 || text || notes;
    }

    public static CustomisationSpec parse(String raw) {
        if (raw == null || raw.isBlank()) return NONE;
        int min = 0, max = 0;
        boolean text = false, textRequired = false, colour = false, size = false, notes = false;
        for (String part : raw.toLowerCase().split("[,;]")) {
            String p = part.trim();
            if (p.startsWith("photo")) {
                int idx = p.indexOf(':');
                String range = idx > 0 ? p.substring(idx + 1).trim() : "1";
                try {
                    if (range.contains("-")) {
                        min = Integer.parseInt(range.substring(0, range.indexOf('-')).trim());
                        max = Integer.parseInt(range.substring(range.indexOf('-') + 1).trim());
                    } else {
                        min = max = Integer.parseInt(range);
                    }
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Bad photo count '" + p + "' (use photo:1 or photo:2-3)");
                }
                if (min < 0 || max < min || max > 5) throw new IllegalArgumentException("Photo count must be between 0 and 5");
            } else if (p.equals("text") || p.equals("name")) {
                text = textRequired = true;
            } else if (p.equals("text?") || p.equals("name?")) {
                text = true;
            } else if (p.equals("colour") || p.equals("color")) {
                colour = true;
            } else if (p.equals("size") || p.equals("variant")) {
                size = true;
            } else if (p.equals("notes")) {
                notes = true;
            } else if (!p.isEmpty()) {
                throw new IllegalArgumentException("Unknown customisation option '" + p + "'");
            }
        }
        return new CustomisationSpec(min, max, text, textRequired, colour, size, notes);
    }
}
