package fr.cnrs.lacito.liftgui.ui.controls;

import java.util.List;

/**
 * Callbacks for adding trait, annotation, field, and note to extensible objects.
 * When null, add buttons are hidden.
 */
public interface ExtensibleAddActions {
    void addTrait(String name, String value);
    void addAnnotation(String name);
    void addField(String type);
    default void addNote(String type) {}
    default void addPronunciation() {}
    default void addRelation(String type) {}
    void refresh();
    List<String> getKnownTraitNames();
    List<String> getKnownAnnotationNames();
    List<String> getKnownFieldTypes();
    default List<String> getKnownNoteTypes() { return List.of(); }
    default List<String> getKnownRelationTypes() { return List.of(); }
}
