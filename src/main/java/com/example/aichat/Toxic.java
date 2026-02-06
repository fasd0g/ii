package com.example.aichat;

public final class Toxic {

    // Не банит и не мутит — только влияет на выбор “calmmod”
    public static double simpleScore(String msg) {
        String m = msg.toLowerCase();
        double s = 0.0;

        // капс
        int letters = 0, upper = 0;
        for (char c : msg.toCharArray()) {
            if (Character.isLetter(c)) {
                letters++;
                if (Character.isUpperCase(c)) upper++;
            }
        }
        if (letters >= 8 && upper >= letters * 0.7) s += 0.8;

        // спам символами
        if (m.matches(".*([!?.])\1{5,}.*")) s += 0.6;

        // мини-словарь ругани
        if (m.contains("идиот") || m.contains("дурак") || m.contains("тупой")
                || m.contains("сука") || m.contains("бля")) s += 1.2;

        return s;
    }
}
