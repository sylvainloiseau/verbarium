/**
 * @author Inès GBADAMASSI
 * @author Maryse GOEH-AKUE
 * @author Ermeline BRESSON
 * @author Ayman JARI
 * @author Erij MAZOUZ
 **/


package fr.cnrs.lacito.liftgui.core;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.LiftDocumentLoadingException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Application logic (dictionary manipulation) will live here.
 */
public final class DictionaryService {

    private static final Logger LOGGER = Logger.getLogger(DictionaryService.class.getName());

    /**
     * Charge un dictionnaire LIFT à partir d'un fichier {@code .lift} via {@code lift-api}.
     *
     * Contraintes:
     * - ne ré-implémente pas le parsing XML (délégué à {@code lift-api})
     * - retourne un dictionnaire en mémoire
     * - gère des erreurs simples (fichier absent, format invalide)
     */
    public LiftDictionary loadFromFile(File file) throws IOException, LiftOpenException {
        if (file == null) {
            throw new IllegalArgumentException("file is null");
        }

        if (!file.exists() || !file.isFile()) {
            throw new FileNotFoundException("Fichier introuvable: " + file.getAbsolutePath());
        }

        if (!Files.isReadable(file.toPath())) {
            throw new IOException("Fichier illisible: " + file.getAbsolutePath());
        }

        String name = file.getName().toLowerCase();
        if (!name.endsWith(".lift")) {
            throw new LiftOpenException("Le fichier doit avoir l’extension .lift");
        }

        // Heuristique légère pour repérer un fichier qui ne ressemble pas du tout à du LIFT.
        // (Sans refaire de parsing XML.)
        if (!looksLikeLift(file)) {
            throw new LiftOpenException("Format invalide: le fichier ne ressemble pas à un document LIFT");
        }

        try {
            return LiftDictionary.loadDictionaryWithFile(file);
        } catch (LiftDocumentLoadingException e) {
            LOGGER.log(Level.SEVERE, "Impossible de charger le fichier LIFT: " + file.getAbsolutePath(), e);
            throw new LiftOpenException("Impossible de charger le fichier LIFT: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            // Le parser SAX peut jeter des IllegalStateException si le XML ne correspond pas à la grammaire attendue.
            LOGGER.log(Level.SEVERE, "Format invalide lors de la lecture du LIFT: " + file.getAbsolutePath(), e);
            throw new LiftOpenException("Format invalide: erreur pendant la lecture du LIFT", e);
        }
    }

    private static boolean looksLikeLift(File file) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            int linesRead = 0;
            while ((line = reader.readLine()) != null && linesRead < 50) {
                linesRead++;
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                if (trimmed.startsWith("<?xml")) continue;
                if (trimmed.startsWith("<!--")) continue;
                return trimmed.contains("<lift") || trimmed.startsWith("<lift");
            }
        }
        return false;
    }
}

