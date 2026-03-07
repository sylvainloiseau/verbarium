/**
 
* @author Inès GBADAMASSI
* @author Maryse GOEH-AKUE
* @author Ermeline BRESSON
* @author Ayman JARI
* @author Erij MAZOUZ

**/

package fr.cnrs.lacito.liftgui.data;

import fr.cnrs.lacito.liftapi.LiftDictionary;

/**
 * Data access layer: will use lift-api to load/save LIFT data.
 * No I/O yet — this is only to anchor the dependency and package boundary.
 */
public final class LiftRepository {
    public LiftDictionary load() {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}

