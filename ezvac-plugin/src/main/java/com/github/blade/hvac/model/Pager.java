package com.github.blade.hvac.model;

import java.util.List;

/**
 * The paging of a sign that shows more than fits: three lines to a page. A
 * click turns the page; auto-flip turns it every few seconds. Shared by the
 * sensor hub and the RPM monitor, which keep their own lists and pass in how
 * many items there are. Which page is showing is never saved.
 */
public final class Pager {
    public static final int PER_PAGE = 3;
    /** How long an auto-flipping sign stays on each page: 5 seconds. */
    public static final long FLIP_TICKS = 100L;

    private boolean autoFlip;
    private int page;
    private long ticksOnPage;

    public boolean autoFlip() { return autoFlip; }

    public void setAutoFlip(boolean autoFlip) {
        this.autoFlip = autoFlip;
        ticksOnPage = 0;
    }

    /** At least one, so an empty sign still has a page to show. */
    public static int pages(int items) {
        return Math.max(1, (items + PER_PAGE - 1) / PER_PAGE);
    }

    /** The page showing, from 0. */
    public int page(int items) { return Math.min(page, pages(items) - 1); }

    public void next(int items) {
        page = (page(items) + 1) % pages(items);
        ticksOnPage = 0;
    }

    /** Keeps the page in range after items were taken away. */
    public void settle(int items) { page = page(items); }

    public <T> List<T> onPage(List<T> items) {
        int from = page(items.size()) * PER_PAGE;
        return items.subList(Math.min(from, items.size()), Math.min(from + PER_PAGE, items.size()));
    }

    /**
     * Advances an auto-flipping sign's clock.
     *
     * @return true when it turned the page
     */
    public boolean advance(long elapsedTicks, int items) {
        if (!autoFlip || pages(items) < 2 || elapsedTicks <= 0) return false;
        ticksOnPage += elapsedTicks;
        if (ticksOnPage < FLIP_TICKS) return false;
        next(items);
        return true;
    }
}
