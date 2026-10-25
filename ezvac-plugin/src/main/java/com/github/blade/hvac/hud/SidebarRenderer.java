package com.github.blade.hvac.hud;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A per-player scoreboard sidebar, Hypixel style: free text lines with the
 * score numbers hidden.
 *
 * <p>A player sees only one sidebar, and the scoreboard also carries things
 * like coloured name tags, so EZvac never takes one that is in use. It only
 * attaches while the player is on the server's main scoreboard and that
 * board has no sidebar and no populated teams. If another plugin later gives
 * the player its own scoreboard, EZvac steps back without a fight and says so
 * once; servers like that are better served by the placeholders.
 */
public final class SidebarRenderer implements HudRenderer {
    private static final int MAX_LINES = HudFormatter.MAX_SIDEBAR_LINES;

    private final HudFormatter format;
    private final Map<UUID, Board> boards = new HashMap<>();

    private static final class Board {
        Scoreboard scoreboard;
        Objective objective;
        final List<Component> lines = new ArrayList<>();
        boolean yielded;
        boolean told;
    }

    public SidebarRenderer(HudFormatter format) {
        this.format = format;
    }

    @Override
    public void render(Player player, ClimateSnapshot snapshot, SectionFrame sections) {
        Board board = boards.computeIfAbsent(player.getUniqueId(), ignored -> new Board());
        if (board.scoreboard != null && !board.yielded && player.getScoreboard() != board.scoreboard) {
            // Something else replaced our board. Don't fight it.
            yieldBoard(player, board);
        }
        if (board.scoreboard == null || board.yielded) {
            if (!canAttach(player)) {
                yieldBoard(player, board);
                return;
            }
            attach(player, board);
        }
        update(board, format.lines(format.medium(snapshot).sidebarLines(), snapshot, sections));
    }

    @Override
    public void clear(Player player) {
        Board board = boards.remove(player.getUniqueId());
        if (board == null || board.scoreboard == null || board.yielded) return;
        if (player.getScoreboard() == board.scoreboard)
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    /** True while the player is on the main board and nothing on it would be hidden by ours. */
    static boolean canAttach(Player player) {
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        if (player.getScoreboard() != main) return false;
        if (main.getObjective(DisplaySlot.SIDEBAR) != null) return false;
        for (Team team : main.getTeams()) if (!team.getEntries().isEmpty()) return false;
        return true;
    }

    private void attach(Player player, Board board) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("ezvac_hud", Criteria.DUMMY, format.title());
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.numberFormat(NumberFormat.blank());
        board.scoreboard = scoreboard;
        board.objective = objective;
        board.lines.clear();
        board.yielded = false;
        board.told = false;
        player.setScoreboard(scoreboard);
    }

    private void yieldBoard(Player player, Board board) {
        board.yielded = true;
        if (board.told) return;
        board.told = true;
        player.sendMessage(Component.text("This server already shows its own scoreboard, so the EZvac"
                + " sidebar is hidden to avoid replacing it. Ask an admin about the EZvac placeholders.",
                NamedTextColor.YELLOW));
    }

    private void update(Board board, List<Component> lines) {
        int count = Math.min(MAX_LINES, lines.size());
        // Air and water layouts, and addon sections, change the line count: drop the extras.
        while (board.lines.size() > count) {
            board.scoreboard.resetScores(entry(board.lines.size() - 1));
            board.lines.remove(board.lines.size() - 1);
        }
        for (int i = 0; i < count; i++) {
            Component line = lines.get(i);
            if (i < board.lines.size() && board.lines.get(i).equals(line)
                    && board.objective.getScore(entry(i)).getScore() == count - i) continue;
            // Higher scores sit higher on the sidebar; the entry names are never shown.
            Score score = board.objective.getScore(entry(i));
            score.setScore(count - i);
            score.customName(line);
            if (i < board.lines.size()) board.lines.set(i, line);
            else board.lines.add(line);
        }
    }

    private static String entry(int line) {
        return "ezvac_" + (line < 10 ? "0" : "") + line;
    }
}
