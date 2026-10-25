package com.github.blade.hvac.hud;

import net.kyori.adventure.text.Component;

import java.util.List;

/**
 * Addon sections (API 1.1) as drawn for one player in one HUD update. Each
 * section is drawn at most once per frame, and only when the HUD's layout
 * asks for it.
 */
public interface SectionFrame {
    /** A frame without sections. */
    SectionFrame NONE = new SectionFrame() {
        @Override public List<String> ids() { return List.of(); }
        @Override public List<Component> lines(String id) { return List.of(); }
        @Override public Component inline(String id) { return Component.empty(); }
    };

    /** Every registered section, in registration order. */
    List<String> ids();

    /** A section's sidebar lines; empty when it is hidden, failed or unknown. */
    List<Component> lines(String id);

    /** A section on one line; empty when it is hidden, failed or unknown. */
    Component inline(String id);
}
