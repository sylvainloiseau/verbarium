package fr.cnrs.lacito.liftgui.undo;

import fr.cnrs.lacito.liftapi.model.LiftExample;
import fr.cnrs.lacito.liftapi.model.LiftFactory;
import fr.cnrs.lacito.liftapi.model.LiftSense;

import java.util.function.Supplier;

/**
 * Commande de suppression d'un exemple, annulable via Undo.
 */
public final class DeleteExampleCommand implements UndoableCommand {
    private final LiftExample example;
    private final LiftSense parent;
    private final int parentIndex;
    private final Supplier<LiftFactory> factorySupplier;
    private final Runnable onUndoRefresh;
    private final Runnable onRedoRefresh;

    public DeleteExampleCommand(LiftExample example, LiftSense parent, int parentIndex,
                                Supplier<LiftFactory> factorySupplier,
                                Runnable onUndoRefresh, Runnable onRedoRefresh) {
        this.example = example;
        this.parent = parent;
        this.parentIndex = parentIndex;
        this.factorySupplier = factorySupplier;
        this.onUndoRefresh = onUndoRefresh;
        this.onRedoRefresh = onRedoRefresh;
    }

    @Override
    public void undo() {
        LiftFactory factory = factorySupplier.get();
        if (factory != null) factory.getAllExamples().add(example);
        parent.getExamples().add(Math.min(parentIndex, parent.getExamples().size()), example);
        if (onUndoRefresh != null) onUndoRefresh.run();
    }

    @Override
    public void redo() {
        LiftFactory factory = factorySupplier.get();
        if (factory != null) factory.getAllExamples().remove(example);
        parent.getExamples().remove(example);
        if (onRedoRefresh != null) onRedoRefresh.run();
    }
}
