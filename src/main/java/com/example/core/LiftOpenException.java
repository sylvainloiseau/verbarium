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

