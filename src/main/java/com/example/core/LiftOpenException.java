/**
 
* @author Inès GBADAMASSI
* @author Maryse GOEH-AKUE
* @author Ermeline BRESSON
* @author Ayman JARI
* @author Erij MAZOUZ

**/


package com.example.core;

/**
 * Erreur fonctionnelle lors de l'ouverture d'un fichier LIFT.
 */
public final class LiftOpenException extends Exception {
    public LiftOpenException(String message) {
        super(message);
    }

    public LiftOpenException(String message, Throwable cause) {
        super(message, cause);
    }
}

