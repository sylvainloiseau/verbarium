package fr.cnrs.lacito.liftgui.undo;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Gestionnaire Undo/Redo pour les modifications du dictionnaire.
 */
public final class UndoManager {
    private final Deque<UndoableCommand> undoStack = new ArrayDeque<>();
    private final Deque<UndoableCommand> redoStack = new ArrayDeque<>();
    private final BooleanProperty canUndo = new SimpleBooleanProperty(false);
    private final BooleanProperty canRedo = new SimpleBooleanProperty(false);

    public void execute(UndoableCommand cmd) {
        redoStack.clear();
        undoStack.push(cmd);
        updateProperties();
    }

    public void undo() {
        if (undoStack.isEmpty()) return;
        UndoableCommand cmd = undoStack.pop();
        cmd.undo();
        redoStack.push(cmd);
        updateProperties();
    }

    public void redo() {
        if (redoStack.isEmpty()) return;
        UndoableCommand cmd = redoStack.pop();
        cmd.redo();
        undoStack.push(cmd);
        updateProperties();
    }

    public void clear() {
        undoStack.clear();
        redoStack.clear();
        updateProperties();
    }

    public ReadOnlyBooleanProperty canUndoProperty() { return canUndo; }
    public ReadOnlyBooleanProperty canRedoProperty() { return canRedo; }
    public boolean canUndo() { return canUndo.get(); }
    public boolean canRedo() { return canRedo.get(); }

    private void updateProperties() {
        canUndo.set(!undoStack.isEmpty());
        canRedo.set(!redoStack.isEmpty());
    }
}
