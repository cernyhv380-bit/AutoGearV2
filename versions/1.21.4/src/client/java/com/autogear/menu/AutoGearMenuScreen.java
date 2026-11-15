package com.autogear.menu;

import com.autogear.actions.GearActions;
import com.autogear.actions.SetHotkeys;
import com.autogear.storage.SetStorage;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Меню AutoGear на ванильных виджетах: категории сетов, поиск, добавление своего сета,
 * предпросмотр содержимого и кнопки действий.
 */
public final class AutoGearMenuScreen extends Screen {

    private static final String TITLE = "AutoGearV2";
    private static final String TELEGRAM = "t.me/gigabyteqq";

    private static final int BTN_W = 100;
    private static final int BTN_H = 20;
    private static final int GAP = 4;
    private static final int TAB_W = 90;
    private static final int TAB_H = 18;
    private static final int LIST_TOP = 60;
    private static final int LIST_ROW = 22;
    private static final int CELL = 18;
    private static final int CELL_GAP = 2;

    private enum Category {
        OWN,   // свои (пользовательские имена)
        AUTO,  // по F8 (авто-сеты set_N)
        READY  // готовые (популярные PvP-сеты)
    }

    private SetList list;
    private Button loadBtn;
    private Button selectBtn;
    private Button deleteBtn;
    private EditBox searchBox;
    private EditBox nameBox;

    private Category category = Category.OWN;
    private String query = "";

    private MenuSets sets = MenuSets.EMPTY;
    private CompletableFuture<MenuSets> catalogTask;
    private CompletableFuture<ItemStack[]> previewTask;
    private String activeSet;
    private String bindingSet;
    private String bindingHint;
    private String previewName;
    private ItemStack[] previewItems;

    private boolean vanishingCurse;

    public AutoGearMenuScreen() {
        super(Component.literal(TITLE));
    }

    @Override
    protected void init() {
        if (this.catalogTask == null && this.sets == MenuSets.EMPTY) {
            this.activeSet = SetStorage.getActiveSet();
            this.vanishingCurse = SetStorage.isVanishingCurseEnabled();
            refreshSets(null);
        }

        int leftW = leftWidth();
        int listHeight = Math.max(40, this.height - LIST_TOP - 96);

        this.list = new SetList(this.minecraft, leftW, listHeight, LIST_TOP, LIST_ROW);
        this.addRenderableWidget(this.list);

        int tabY = LIST_TOP - 24;
        this.addRenderableWidget(Button.builder(Component.literal("Свои"), b -> setCategory(Category.OWN))
                .bounds(10, tabY, TAB_W, TAB_H)
                .tooltip(tip("Свои сеты из папки AutoGear/sets — всё, что не set_N и не готовые"))
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("По F8"), b -> setCategory(Category.AUTO))
                .bounds(10 + (TAB_W + GAP), tabY, TAB_W, TAB_H)
                .tooltip(tip("Авто-сеты set_N, которые создаются клавишей F8"))
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("Готовые"), b -> setCategory(Category.READY))
                .bounds(10 + (TAB_W + GAP) * 2, tabY, TAB_W, TAB_H)
                .tooltip(tip("Сеты из папки AutoGear/ready (можно кидать туда файлы из канала)"))
                .build());

        this.addRenderableWidget(Button.builder(curseLabel(), b -> {
            this.vanishingCurse = !this.vanishingCurse;
            SetStorage.setVanishingCurseEnabled(this.vanishingCurse);
            b.setMessage(curseLabel());
            refreshSets(this.vanishingCurse);
        }).bounds(Math.max(10, this.width - 176), 6, 166, TAB_H)
                .tooltip(tip("Проклятие утраты: ВКЛ — добавить его на все предметы готовых "
                        + "сетов, ВЫКЛ — вырезать"))
                .build());

        int searchX = 10 + (TAB_W + GAP) * 3;
        int searchW = leftW - searchX - 10;
        if (searchW >= 80) {
            this.searchBox = new EditBox(this.font, searchX, tabY, searchW, TAB_H, Component.literal("Поиск..."));
            this.searchBox.setMaxLength(32);
            this.searchBox.setTooltip(tip("Поиск сразу по всем категориям: и «Свои», и «По F8», и «Готовые»"));
            this.searchBox.setResponder(text -> {
                this.query = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
                this.list.reload();
            });
            this.addRenderableWidget(this.searchBox);
        }

        this.list.reload();

        // Добавление своего сета
        int addRowY = this.height - 58;
        this.nameBox = new EditBox(this.font, this.width / 2 - 160, addRowY, 200, BTN_H, Component.literal("Имя сета"));
        this.nameBox.setMaxLength(24);
        this.nameBox.setTooltip(tip("Имя нового сета: сохранит текущий инвентарь под этим именем"));
        this.addRenderableWidget(this.nameBox);
        this.addRenderableWidget(Button.builder(Component.literal("Добавить"), b -> {
            String name = this.nameBox.getValue() == null ? "" : this.nameBox.getValue().trim();
            if (name.isEmpty()) return;
            GearActions.saveCurrentAs(name);
            refreshSets(null);
        }).bounds(this.width / 2 + 48, addRowY, 112, BTN_H)
                .tooltip(tip("Сохранить текущий инвентарь как новый сет с этим именем"))
                .build());

        // Кнопки действий
        int folderW = 90;
        int tgW = 52;
        int total = BTN_W * 5 + GAP * 4 + GAP + folderW + GAP + tgW;
        int x = Math.max(6, (this.width - total) / 2);
        int y = this.height - 32;

        this.loadBtn = this.addRenderableWidget(Button.builder(Component.literal("Загрузить"), b -> {
            String name = this.list.selectedName();
            if (name == null || this.catalogTask != null) return;
            // Меню закрываем, чтобы загрузка шла как по F4 — с теми же переключениями режима.
            this.onClose();
            GearActions.loadFromMenu(name);
        }).bounds(x, y, BTN_W, BTN_H)
                .tooltip(tip("Надеть выбранный сет. В выживании на разрешённых серверах "
                        + "включит креатив и вернёт режим — как F4"))
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("Сохранить"), b -> {
            GearActions.saveCurrentAsNew();
            refreshSets(null);
        }).bounds(x + (BTN_W + GAP), y, BTN_W, BTN_H)
                .tooltip(tip("Сохранить текущий инвентарь как новый сет (set_N, как F8)"))
                .build());

        this.selectBtn = this.addRenderableWidget(Button.builder(Component.literal("Активный"), b -> {
            String name = this.list.selectedName();
            if (name != null) {
                GearActions.select(name);
                this.activeSet = SetStorage.getActiveSet();
                this.list.reload();
            }
        }).bounds(x + (BTN_W + GAP) * 2, y, BTN_W, BTN_H)
                .tooltip(tip("Сделать выбранный сет активным — его будет надевать F4"))
                .build());

        this.deleteBtn = this.addRenderableWidget(Button.builder(Component.literal("Удалить"), b -> {
            String name = this.list.selectedName();
            if (name != null) {
                if (SetStorage.deleteSet(name)) {
                    if (name.equals(this.activeSet)) {
                        SetStorage.setActiveSet(null);
                        this.activeSet = null;
                    }
                    refreshSets(null);
                }
            }
        }).bounds(x + (BTN_W + GAP) * 3, y, BTN_W, BTN_H)
                .tooltip(tip("Удалить файл сета с диска — восстановить будет нельзя"))
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("Закрыть"), b -> this.onClose())
                .bounds(x + (BTN_W + GAP) * 4, y, BTN_W, BTN_H)
                .tooltip(tip("Закрыть меню (открывается клавишей G)"))
                .build());

        int folderX = x + (BTN_W + GAP) * 5;
        int tgX = folderX + folderW + GAP;

        this.addRenderableWidget(Button.builder(Component.literal("Папка"), b -> openReadyFolder())
                .bounds(folderX, y, folderW, BTN_H)
                .tooltip(tip("Открыть папку AutoGear/ready — сюда кидают готовые сеты"))
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("TG"), b -> openTelegram())
                .bounds(tgX, y, tgW, BTN_H)
                .tooltip(tip("Открыть канал " + TELEGRAM))
                .build());

        updateButtons();
    }

    /** Подсказка при наведении на виджет. */
    private static Tooltip tip(String text) {
        return Tooltip.create(Component.literal(text));
    }

    /** Надпись тумблера с текущим состоянием. */
    private Component curseLabel() {
        return Component.literal("Проклятие утраты: "
                + (this.vanishingCurse ? "ВКЛ" : "ВЫКЛ"));
    }

    /** Открывает папку с готовыми сетами в проводнике. */
    private static void openReadyFolder() {
        try {
            Files.createDirectories(SetStorage.readyDir());
        } catch (IOException ignored) {
            // если папку создать не удалось, всё равно пробуем открыть
        }
        Util.getPlatform().openPath(SetStorage.readyDir());
    }

    /** Открывает канал в браузере. */
    private static void openTelegram() {
        Util.getPlatform().openUri(URI.create("https://" + TELEGRAM));
    }

    private int leftWidth() {
        return Math.max(220, this.width / 2 - 20);
    }

    private void setCategory(Category newCategory) {
        if (this.category == newCategory) return;
        this.category = newCategory;
        this.list.reload();
        updateButtons();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == InputConstants.MOUSE_BUTTON_MIDDLE && this.list != null) {
            SetEntry entry = this.list.entryAt(mouseX, mouseY);
            return beginBinding(entry == null ? null : entry.name);
        }
        this.bindingSet = null;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(g);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(this.font, Component.literal(TITLE), this.width / 2, 10, 0xFFFFFFFF);
        g.drawCenteredString(this.font, Component.literal(TELEGRAM), this.width / 2, 22, 0xFFAAAAAA);
        drawPreview(g, mouseX, mouseY);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.catalogTask != null && this.catalogTask.isDone()) {
            this.sets = this.catalogTask.getNow(this.sets);
            this.catalogTask = null;
            this.list.reload();
            clearPreview();
        }
        updatePreview(this.list.selectedName());
        updateButtons();
    }

    private void updateButtons() {
        if (this.list == null) return;
        boolean hasSelection = this.list.selectedName() != null;
        if (this.loadBtn != null) this.loadBtn.active = hasSelection && this.catalogTask == null;
        if (this.selectBtn != null) this.selectBtn.active = hasSelection;
        if (this.deleteBtn != null) this.deleteBtn.active = hasSelection;
    }

    // ============================ Предпросмотр ============================

    private void drawPreview(GuiGraphics g, int mouseX, int mouseY) {
        int px = leftWidth() + 16;
        int py = LIST_TOP;
        int pw = this.width - px - 12;
        int ph = Math.max(40, this.height - LIST_TOP - 96);
        if (pw < 100) return;

        g.fill(px, py, px + pw, py + ph, 0x66000000);

        if (this.bindingSet != null) {
            g.drawString(this.font, "Бинд: " + this.bindingSet, px + 8, py + 8, 0xFFFFFFFF);
            int hintY = py + 24;
            for (var line : this.font.split(Component.literal(this.bindingHint), pw - 16)) {
                g.drawString(this.font, line, px + 8, hintY, 0xFFAAAAAA);
                hintY += this.font.lineHeight;
            }
            return;
        }

        String name = this.list.selectedName();
        if (name == null) {
            g.drawString(this.font, this.catalogTask != null ? "Загрузка списка сетов..." : "Нет выбранного сета", px + 8, py + 8, 0xFFAAAAAA);
            return;
        }

        g.drawString(this.font, "Сет: " + name, px + 8, py + 8, 0xFFFFFFFF);

        if (!name.equals(this.previewName) || this.previewTask != null || this.catalogTask != null) {
            g.drawString(this.font, "Загрузка предпросмотра...", px + 8, py + 24, 0xFFAAAAAA);
            return;
        }

        if (this.previewItems == null) {
            g.drawString(this.font, "Не удалось прочитать содержимое", px + 8, py + 24, 0xFFAAAAAA);
            return;
        }

        int top = py + 26;

        // Броня (шлем, нагрудник, штаны, ботинки) и левая рука
        int[] armorSlots = {39, 38, 37, 36, 40};
        for (int i = 0; i < armorSlots.length; i++) {
            drawCell(g, mouseX, mouseY, px + 8, top + i * (CELL + CELL_GAP), this.previewItems[armorSlots[i]]);
        }

        // Инвентарь 9x3 и хотбар
        int gridX = px + 8 + CELL + 12;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int idx = 9 + row * 9 + col;
                drawCell(g, mouseX, mouseY,
                        gridX + col * (CELL + CELL_GAP), top + row * (CELL + CELL_GAP),
                        this.previewItems[idx]);
            }
        }
        int hotbarY = top + 3 * (CELL + CELL_GAP) + 6;
        for (int col = 0; col < 9; col++) {
            drawCell(g, mouseX, mouseY,
                    gridX + col * (CELL + CELL_GAP), hotbarY,
                    this.previewItems[col]);
        }
    }

    private void drawCell(GuiGraphics g, int mouseX, int mouseY, int x, int y, ItemStack stack) {
        g.fill(x, y, x + CELL, y + CELL, 0x40000000);
        if (stack == null || stack.isEmpty()) return;

        g.renderItem(stack, x + 1, y + 1);
        g.renderItemDecorations(this.font, stack, x + 1, y + 1);

        if (mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL) {
            g.renderTooltip(this.font, stack, mouseX, mouseY);
        }
    }

    private void refreshSets(Boolean normalizeCurse) {
        clearPreview();
        MenuSets previous = this.sets;
        this.catalogTask = MenuSets.refresh(normalizeCurse).exceptionally(error -> previous);
    }

    private void clearPreview() {
        if (this.previewTask != null) this.previewTask.cancel(false);
        this.previewTask = null;
        this.previewName = null;
        this.previewItems = null;
    }

    private void updatePreview(String name) {
        if (this.catalogTask != null) return;
        if (!Objects.equals(name, this.previewName)) {
            clearPreview();
            this.previewName = name;
            RegistryAccess registries = this.minecraft.level == null ? null : this.minecraft.level.registryAccess();
            if (name != null && registries != null) {
                this.previewTask = MenuSets.preview(name, registries);
            }
        }
        if (this.previewTask != null && this.previewTask.isDone()) {
            this.previewItems = this.previewTask.isCompletedExceptionally() ? null : this.previewTask.getNow(null);
            this.previewTask = null;
        }
    }

    @Override
    public void removed() {
        clearPreview();
        super.removed();
    }

    private boolean beginBinding(String name) {
        if (name == null) return false;
        this.bindingSet = name;
        this.bindingHint = "Нажми клавишу. Delete или Esc — снять бинд.";
        setFocused(null);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (this.bindingSet == null) return super.keyPressed(key, scanCode, modifiers);
        boolean remove = key == InputConstants.KEY_DELETE || key == InputConstants.KEY_ESCAPE;
        if (key > 0) {
            if (!remove && SetHotkeys.isReserved(key)) {
                this.bindingHint = "Клавиша занята. Выбери другую.";
            } else if (SetStorage.setKeyBinding(this.bindingSet, remove ? null : key)) {
                this.bindingSet = null;
            } else {
                this.bindingHint = "Не удалось сохранить изменение бинда. Попробуй ещё раз.";
            }
        }
        return true;
    }

    private void drawSetLabel(GuiGraphics g, String name, String label, int x, int right, int y, int color) {
        Integer key = SetStorage.getSetKeyBindings().get(name);
        String bind = name.equals(this.bindingSet) ? "[...]" : key == null ? "" : "[" + SetHotkeys.label(key) + "]";
        int labelWidth = right - x;
        if (!bind.isEmpty()) {
            int bindX = right - this.font.width(bind);
            g.drawString(this.font, bind, bindX, y, 0xFFFFD479);
            labelWidth = bindX - x - 6;
        }
        if (labelWidth > 0) {
            g.drawString(this.font, this.font.plainSubstrByWidth(label, labelWidth), x, y, color);
        }
    }

    // ============================ Список ============================

    /** Авто-сеты, сохраняемые по F8 как set_N. */
    private static boolean isAutoSet(String name) {
        return name.matches("set_\\d+");
    }

    /** Готовые PvP-сеты (по префиксу имени). */
    private static boolean isReadySet(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.startsWith("pvp_") || lower.startsWith("ready_") || lower.startsWith("popular_");
    }

    private boolean inCategory(String name) {
        boolean auto = isAutoSet(name);
        boolean ready = isReadySet(name);
        return switch (this.category) {
            case OWN -> !auto && !ready;
            case AUTO -> auto;
            // список готовых уже собран из папки ready/, фильтр по имени не нужен
            case READY -> true;
        };
    }

    private final class SetList extends ObjectSelectionList<SetEntry> {

        SetList(Minecraft mc, int width, int height, int y0, int itemHeight) {
            super(mc, width, height, y0, itemHeight);
        }

        void reload() {
            String previouslySelected = selectedName();
            clearEntries();

            boolean searching = !query.isEmpty();
            Set<String> source = new LinkedHashSet<>();
            if (searching || category != Category.READY) source.addAll(sets.own());
            if (searching || category == Category.READY) source.addAll(sets.ready());
            for (String name : source) {
                if (!searching && !inCategory(name)) continue;
                if (searching && !name.toLowerCase(Locale.ROOT).contains(query)) continue;
                addEntry(new SetEntry(name, name.equals(activeSet)));
            }

            if (children().isEmpty()) {
                return;
            }

            SetEntry first = children().get(0);
            SetEntry target = first;
            if (previouslySelected != null) {
                for (SetEntry entry : children()) {
                    if (entry.name.equals(previouslySelected)) {
                        target = entry;
                        break;
                    }
                }
            }
            setSelected(target);
        }

        SetEntry entryAt(double mouseX, double mouseY) {
            return isMouseOver(mouseX, mouseY) ? getEntryAtPosition(mouseX, mouseY) : null;
        }

        String selectedName() {
            SetEntry entry = getSelected();
            return entry == null ? null : entry.name;
        }
    }

    /** Строка списка: имя сета и пометка активного. */
    private final class SetEntry extends ObjectSelectionList.Entry<SetEntry> {

        private final String name;
        private final boolean active;

        SetEntry(String name, boolean active) {
            this.name = name;
            this.active = active;
        }

        @Override
        public void render(GuiGraphics g, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float partialTick) {
            String label = this.active ? this.name + "  (активный)" : this.name;
            int textY = y + (entryHeight - 9) / 2;
            drawSetLabel(g, this.name, label, x + 4, x + entryWidth - 4, y + (entryHeight - 9) / 2, 0xFFFFFFFF);
        }

        @Override
        public Component getNarration() {
            return Component.literal(this.name);
        }
    }
}




