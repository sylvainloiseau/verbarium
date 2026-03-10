package fr.cnrs.lacito.liftgui.undo;

import fr.cnrs.lacito.liftapi.model.LiftFactory;
import fr.cnrs.lacito.liftapi.model.LiftSense;

import java.util.List;
import java.util.function.Supplier;

/**
 * Commande de suppression d'un sens, annulable via Undo.
 */
public final class DeleteSenseCommand implements UndoableCommand {
    private final LiftSense sense;
    private final List<LiftSense> parentList;
    private final int parentIndex;
    private final Supplier<LiftFactory> factorySupplier;
    private final Runnable onUndoRefresh;
    private final Runnable onRedoRefresh;

    public DeleteSenseCommand(LiftSense sense, List<LiftSense> parentList, int parentIndex,
                              Supplier<LiftFactory> factorySupplier,
                              Runnable onUndoRefresh, Runnable onRedoRefresh) {
        this.sense = sense;
        this.parentList = parentList;
        this.parentIndex = parentIndex;
        this.factorySupplier = factorySupplier;
        this.onUndoRefresh = onUndoRefresh;
        this.onRedoRefresh = onRedoRefresh;
    }

    @Override
    public void undo() {
        LiftFactory factory = factorySupplier.get();
        if (factory != null) factory.getAllSenses().add(sense);
        parentList.add(Math.min(parentIndex, parentList.size()), sense);
        if (onUndoRefresh != null) onUndoRefresh.run();
    }

    @Override
    public void redo() {
        LiftFactory factory = factorySupplier.get();
        if (factory != null) factory.getAllSenses().remove(sense);
        parentList.remove(sense);
        if (onRedoRefresh != null) onRedoRefresh.run();
    }
}
