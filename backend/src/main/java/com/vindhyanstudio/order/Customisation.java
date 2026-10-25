package com.vindhyanstudio.order;

import java.util.List;

/** What the customer entered on the product page. Photos are private storage keys from /api/uploads. */
public record Customisation(String text, String colour, String size, String notes, List<String> photos) {

    public boolean isEmpty() {
        return blank(text) && blank(colour) && blank(size) && blank(notes) && (photos == null || photos.isEmpty());
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
