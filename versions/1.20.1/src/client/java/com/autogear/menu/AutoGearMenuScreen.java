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
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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
    private Button curseBtn;
    private EditBox searchBox;
    private EditBox nameBox;

    // New installs have bundled presets but no user sets, so open on a populated tab.
    private Category category = Category.READY;
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
        this.list = new SetList();

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

        this.curseBtn = this.addRenderableWidget(Button.builder(curseLabel(), b -> toggleCurse(b)).bounds(curseButtonX(), 6, 166, TAB_H)
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
            addNamedSet();
        }).bounds(this.width / 2 + 48, addRowY, 112, BTN_H)
                .tooltip(tip("Сохранить текущий инвентарь как новый сет с этим именем"))
                .build());

        // Кнопки действий
        int folderW = 90;
        int tgW = 52;
        int x = bottomButtonsX();
        int y = bottomButtonsY();

        this.loadBtn = this.addRenderableWidget(Button.builder(Component.literal("Загрузить"), b -> {
            loadSelectedSet();
        }).bounds(x, y, BTN_W, BTN_H)
                .tooltip(tip("Надеть выбранный сет. В выживании на разрешённых серверах "
                        + "включит креатив и вернёт режим — как F4"))
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("Сохранить"), b -> {
            saveCurrentSet();
        }).bounds(x + (BTN_W + GAP), y, BTN_W, BTN_H)
                .tooltip(tip("Сохранить текущий инвентарь как новый сет (set_N, как F8)"))
                .build());

        this.selectBtn = this.addRenderableWidget(Button.builder(Component.literal("Активный"), b -> {
            selectCurrentSet();
        }).bounds(x + (BTN_W + GAP) * 2, y, BTN_W, BTN_H)
                .tooltip(tip("Сделать выбранный сет активным — его будет надевать F4"))
                .build());

        this.deleteBtn = this.addRenderableWidget(Button.builder(Component.literal("Удалить"), b -> {
            deleteCurrentSet();
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

    private int curseButtonX() { return Math.max(10, this.width - 176); }

    private int bottomButtonsX() {
        int folderW = 90;
        int tgW = 52;
        int total = BTN_W * 5 + GAP * 4 + GAP + folderW + GAP + tgW;
        return Math.max(6, (this.width - total) / 2);
    }

    private int bottomButtonsY() { return this.height - 32; }

    private static boolean inside(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    private void toggleCurse(Button button) {
        this.vanishingCurse = !this.vanishingCurse;
        SetStorage.setVanishingCurseEnabled(this.vanishingCurse);
        button.setMessage(curseLabel());
        refreshSets(this.vanishingCurse);
    }

    private void addNamedSet() {
        String name = this.nameBox == null || this.nameBox.getValue() == null
                ? "" : this.nameBox.getValue().trim();
        if (name.isEmpty()) return;
        GearActions.saveCurrentAs(name);
        refreshSets(null);
    }

    private void saveCurrentSet() {
        GearActions.saveCurrentAsNew();
        refreshSets(null);
    }

    private void loadSelectedSet() {
        String name = this.list == null ? null : this.list.selectedName();
        if (name == null || this.catalogTask != null) return;
        this.onClose();
        GearActions.loadFromMenu(name);
    }

    private void selectCurrentSet() {
        String name = this.list == null ? null : this.list.selectedName();
        if (name != null) {
            GearActions.select(name);
            this.activeSet = SetStorage.getActiveSet();
            this.list.reload();
        }
    }

    private void deleteCurrentSet() {
        String name = this.list == null ? null : this.list.selectedName();
        if (name != null) {
            if (SetStorage.deleteSet(name)) {
                if (name.equals(this.activeSet)) {
                    SetStorage.setActiveSet(null);
                    this.activeSet = null;
                }
                refreshSets(null);
            }
        }
    }

    /** Открывает папку с готовыми сетами в проводнике. */
    private static void openReadyFolder() {
        try {
            Files.createDirectories(SetStorage.readyDir());
        } catch (IOException ignored) {
            // если папку создать не удалось, всё равно пробуем открыть
        }
        Util.getPlatform().openFile(SetStorage.readyDir().toFile());
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
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        if (this.list != null) this.list.render(g, mouseX, mouseY);
        super.render(g, mouseX, mouseY, partialTick);
        int panelX = leftWidth() + 16;
        int panelWidth = this.width - panelX - 12;
        g.drawCenteredString(this.font, Component.literal(TITLE), panelX + panelWidth / 2, 10, 0xFFFFFFFF);
        g.drawCenteredString(this.font, Component.literal(TELEGRAM), panelX + panelWidth / 2, 22, 0xFFAAAAAA);
        drawPreview(g, mouseX, mouseY);
    }

    /** Route menu actions directly by their visible bounds, independent of widget event dispatch. */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == InputConstants.MOUSE_BUTTON_MIDDLE && this.list != null) {
            return beginBinding(this.list.nameAt(mouseX, mouseY));
        }
        this.bindingSet = null;
        if (button == 0) {
            int tabY = LIST_TOP - 24;
            if (inside(mouseX, mouseY, 10, tabY, TAB_W, TAB_H)) {
                setCategory(Category.OWN);
                return true;
            }
            if (inside(mouseX, mouseY, 10 + TAB_W + GAP, tabY, TAB_W, TAB_H)) {
                setCategory(Category.AUTO);
                return true;
            }
            if (inside(mouseX, mouseY, 10 + (TAB_W + GAP) * 2, tabY, TAB_W, TAB_H)) {
                setCategory(Category.READY);
                return true;
            }
            if (inside(mouseX, mouseY, curseButtonX(), 6, 166, TAB_H)) {
                if (this.curseBtn != null) toggleCurse(this.curseBtn);
                return true;
            }
            if (this.list != null && this.list.selectAt(mouseX, mouseY)) return true;

            int addRowY = this.height - 58;
            if (inside(mouseX, mouseY, this.width / 2 + 48, addRowY, 112, BTN_H)) {
                addNamedSet();
                return true;
            }

            int x = bottomButtonsX();
            int y = bottomButtonsY();
            if (inside(mouseX, mouseY, x, y, BTN_W, BTN_H)) {
                loadSelectedSet();
                return true;
            }
            if (inside(mouseX, mouseY, x + BTN_W + GAP, y, BTN_W, BTN_H)) {
                saveCurrentSet();
                return true;
            }
            if (inside(mouseX, mouseY, x + (BTN_W + GAP) * 2, y, BTN_W, BTN_H)) {
                selectCurrentSet();
                return true;
            }
            if (inside(mouseX, mouseY, x + (BTN_W + GAP) * 3, y, BTN_W, BTN_H)) {
                deleteCurrentSet();
                return true;
            }
            if (inside(mouseX, mouseY, x + (BTN_W + GAP) * 4, y, BTN_W, BTN_H)) {
                this.onClose();
                return true;
            }
            int folderX = x + (BTN_W + GAP) * 5;
            if (inside(mouseX, mouseY, folderX, y, 90, BTN_H)) {
                openReadyFolder();
                return true;
            }
            if (inside(mouseX, mouseY, folderX + 90 + GAP, y, 52, BTN_H)) {
                openTelegram();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.list != null && this.list.scroll(mouseX, mouseY, delta)) return true;
        return super.mouseScrolled(mouseX, mouseY, delta);
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

    private final class SetList {

        private final List<String> names = new ArrayList<>();
        private String selected;
        private int scrollOffset;

        void reload() {
            String previouslySelected = this.selected;
            this.names.clear();

            boolean searching = !query.isEmpty();
            Set<String> source = new LinkedHashSet<>();
            if (searching || category != Category.READY) source.addAll(sets.own());
            if (searching || category == Category.READY) source.addAll(sets.ready());
            for (String name : source) {
                if (!searching && !inCategory(name)) continue;
                if (searching && !name.toLowerCase(Locale.ROOT).contains(query)) continue;
                this.names.add(name);
            }

            this.selected = this.names.contains(previouslySelected)
                    ? previouslySelected : (this.names.isEmpty() ? null : this.names.get(0));
            clampScroll();
            updateButtons();
        }

        String selectedName() {
            return this.selected;
        }

        private int listBottom() {
            return Math.max(LIST_TOP + 40, AutoGearMenuScreen.this.height - 96);
        }

        private int visibleRows() {
            return Math.max(1, (listBottom() - LIST_TOP) / LIST_ROW);
        }

        private int maxScroll() {
            return Math.max(0, this.names.size() - visibleRows());
        }

        private void clampScroll() {
            this.scrollOffset = Math.max(0, Math.min(this.scrollOffset, maxScroll()));
        }

        String nameAt(double mouseX, double mouseY) {
            if (!inside(mouseX, mouseY, 0, LIST_TOP, leftWidth(), listBottom() - LIST_TOP)) return null;
            int row = (int) ((mouseY - LIST_TOP) / LIST_ROW);
            int index = this.scrollOffset + row;
            return row < visibleRows() && index < this.names.size() ? this.names.get(index) : null;
        }

        boolean selectAt(double mouseX, double mouseY) {
            String next = nameAt(mouseX, mouseY);
            if (next == null) return false;
            this.selected = next;
            updateButtons();
            return true;
        }

        boolean scroll(double mouseX, double mouseY, double delta) {
            if (!inside(mouseX, mouseY, 0, LIST_TOP, leftWidth(), listBottom() - LIST_TOP)) return false;
            if (delta != 0) {
                this.scrollOffset -= (int) Math.signum(delta);
                clampScroll();
            }
            return true;
        }

        void render(GuiGraphics g, int mouseX, int mouseY) {
            int width = leftWidth();
            int bottom = listBottom();
            int visible = visibleRows();
            g.fill(0, LIST_TOP, width, bottom, 0xB0101010);
            g.enableScissor(0, LIST_TOP, width, bottom);

            if (this.names.isEmpty()) {
                g.drawString(font, "В этой категории сетов нет", 12, LIST_TOP + 8, 0xFFAAAAAA);
            } else {
                int end = Math.min(this.names.size(), this.scrollOffset + visible);
                for (int index = this.scrollOffset; index < end; index++) {
                    int y = LIST_TOP + (index - this.scrollOffset) * LIST_ROW;
                    String name = this.names.get(index);
                    boolean hovered = inside(mouseX, mouseY, 0, y, width, LIST_ROW);
                    if (name.equals(this.selected)) {
                        g.fill(2, y, width - 2, y + LIST_ROW - 1, 0xCC365A78);
                    } else if (hovered) {
                        g.fill(2, y, width - 2, y + LIST_ROW - 1, 0x66444444);
                    }
                    String label = name.equals(activeSet) ? name + "  (активный)" : name;
                    int color = name.equals(activeSet) ? 0xFFB8E986 : 0xFFFFFFFF;
                    drawSetLabel(g, name, label, 8, width - 10, y + (LIST_ROW - 9) / 2, color);
                }
            }

            if (this.names.size() > visible) {
                int trackX = width - 4;
                g.fill(trackX, LIST_TOP, width, bottom, 0x66333333);
                int thumbHeight = Math.max(12, (bottom - LIST_TOP) * visible / this.names.size());
                int travel = bottom - LIST_TOP - thumbHeight;
                int thumbY = LIST_TOP + (maxScroll() == 0 ? 0 : travel * this.scrollOffset / maxScroll());
                g.fill(trackX, thumbY, width, thumbY + thumbHeight, 0xFFAAAAAA);
            }
            g.disableScissor();
        }
    }
}




