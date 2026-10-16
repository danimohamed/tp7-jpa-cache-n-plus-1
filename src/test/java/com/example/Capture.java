package com.example;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Détourne temporairement {@link System#out} et {@link System#err} pour permettre aux tests de
 * vérifier ce qu'une démonstration affiche.
 *
 * <p>Usage : {@code try (Capture c = new Capture()) { ... c.sortie(); c.erreur(); }}. Le test doit
 * impérativement fermer la capture, sinon la sortie de Maven reste redirigée vers un tampon
 * mémoire et le rapport de Surefire devient illisible.</p>
 */
public final class Capture implements AutoCloseable {

    private final PrintStream sortieOriginelle = System.out;
    private final PrintStream erreurOriginele = System.err;

    private final ByteArrayOutputStream tamponSortie = new ByteArrayOutputStream();
    private final ByteArrayOutputStream tamponErreur = new ByteArrayOutputStream();

    public Capture() {
        System.setOut(new PrintStream(tamponSortie, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(tamponErreur, true, StandardCharsets.UTF_8));
    }

    public String sortie() {
        System.out.flush();
        return new String(tamponSortie.toByteArray(), StandardCharsets.UTF_8);
    }

    public String erreur() {
        System.err.flush();
        return new String(tamponErreur.toByteArray(), StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        System.setOut(sortieOriginelle);
        System.setErr(erreurOriginele);
        try {
            tamponSortie.close();
            tamponErreur.close();
        } catch (java.io.IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
