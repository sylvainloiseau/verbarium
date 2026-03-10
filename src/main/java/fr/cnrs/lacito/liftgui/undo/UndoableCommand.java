package fr.cnrs.lacito.liftgui.undo;

/**
 * Commande annulable pour le système Undo/Redo.
 */
public interface UndoableCommand {
    void undo();
    void redo();
}
