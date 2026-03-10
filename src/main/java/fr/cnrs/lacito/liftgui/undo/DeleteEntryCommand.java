package fr.cnrs.lacito.liftgui.undo;

import fr.cnrs.lacito.liftapi.model.LiftEntry;
import fr.cnrs.lacito.liftapi.model.LiftFactory;
import javafx.collections.ObservableList;

import java.util.function.Supplier;

/**
 * Commande de suppression d'une entrée, annulable via Undo.
 */
public final class DeleteEntryCommand implements UndoableCommand {
    private final LiftEntry entry;
    private final int baseEntriesIndex;
    private final Supplier<LiftFactory> factorySupplier;
    private final ObservableList<LiftEntry> baseEntries;
    private final Runnable onUndoRefresh;
    private final Runnable onRedoRefresh;

    public DeleteEntryCommand(LiftEntry entry, int baseEntriesIndex,
                              Supplier<LiftFactory> factorySupplier,
                              ObservableList<LiftEntry> baseEntries,
                              Runnable onUndoRefresh, Runnable onRedoRefresh) {
        this.entry = entry;
        this.baseEntriesIndex = baseEntriesIndex;
        this.factorySupplier = factorySupplier;
        this.baseEntries = baseEntries;
        this.onUndoRefresh = onUndoRefresh;
        this.onRedoRefresh = onRedoRefresh;
    }

    @Override
    public void undo() {
        LiftFactory factory = factorySupplier.get();
        if (factory != null) {
            factory.getAllEntries().add(entry);
            factory.getAllObjectLanguagesMultiText().add(entry.getForms());
            factory.getAllMetaLanguagesMultiText().add(entry.getCitations());
        }
        int idx = Math.min(baseEntriesIndex, baseEntries.size());
        baseEntries.add(idx, entry);
        if (onUndoRefresh != null) onUndoRefresh.run();
    }

    @Override
    public void redo() {
        LiftFactory factory = factorySupplier.get();
        if (factory != null) {
            factory.getAllEntries().remove(entry);
            factory.getAllObjectLanguagesMultiText().remove(entry.getForms());
            factory.getAllMetaLanguagesMultiText().remove(entry.getCitations());
        }
        baseEntries.remove(entry);
        if (onRedoRefresh != null) onRedoRefresh.run();
    }
}
