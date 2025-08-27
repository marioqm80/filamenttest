package com.example.filamenttestjava.utils;

public final class IgcParser {
    private IgcParser() {}

    /**
     * Converte um B-record IGC em latitude/longitude decimais (WGS84).
     * Exemplo de entrada: "B1226083742168N00359302WA017940185600980412900646"
     * Retorna: new double[]{ lat, lon }
     */
    public static double[] parseBRecordLatLon(String b) {
        if (b == null || b.length() < 24 || b.charAt(0) != 'B') {
            throw new IllegalArgumentException("B-record inválido");
        }

        int idx = 1;                 // pula 'B'
        idx += 6;                    // HHMMSS (tempo)

        // Latitude: DDMMmmm + N/S
        String lat = b.substring(idx, idx + 7); idx += 7;
        char ns = b.charAt(idx);     idx += 1;

        // Longitude: DDDMMmmm + E/W
        String lon = b.substring(idx, idx + 8); idx += 8;
        char ew = b.charAt(idx);     // idx += 1; (não precisamos prosseguir)

        // Parse latitude
        int latDeg = Integer.parseInt(lat.substring(0, 2));
        int latMin = Integer.parseInt(lat.substring(2, 4));
        int latThou = Integer.parseInt(lat.substring(4, 7)); // milésimos de minuto
        double latDecimal = latDeg + (latMin + latThou / 1000.0) / 60.0;
        if (ns == 'S' || ns == 's') latDecimal = -latDecimal;

        // Parse longitude
        int lonDeg = Integer.parseInt(lon.substring(0, 3));
        int lonMin = Integer.parseInt(lon.substring(3, 5));
        int lonThou = Integer.parseInt(lon.substring(5, 8));
        double lonDecimal = lonDeg + (lonMin + lonThou / 1000.0) / 60.0;
        if (ew == 'W' || ew == 'w') lonDecimal = -lonDecimal;

        return new double[]{ latDecimal, lonDecimal };
    }
}
