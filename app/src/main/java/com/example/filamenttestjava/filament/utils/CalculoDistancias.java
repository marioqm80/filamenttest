package com.example.filamenttestjava.filament.utils;

import android.location.Location;

public class CalculoDistancias {
    public static double[] getPixelModelo(double latitudeCentro, double longitudeCentro, double latitude, double longitude) {


        double[] output = new double[2];

        double distanciaLat = distance(latitudeCentro, longitudeCentro, latitude, longitudeCentro);
        double distanciaLon = distance(latitudeCentro, longitudeCentro, latitudeCentro, longitude);



        //System.out.println("lat = " + takeoffLat + " lon = " + takeoffLon);

        output[1] =  ( latitude > latitudeCentro?distanciaLat:-distanciaLat);
        output[0] =  ( longitude > longitudeCentro?distanciaLon:-distanciaLon);
        return output;
    }
    public static double distance(double startLat, double startLong,
                                  double endLat, double endLong) {
        /*

        int EARTH_RADIUS = 6371;
        double dLat = Math.toRadians((endLat - startLat));
        double dLong = Math.toRadians((endLong - startLong));

        startLat = Math.toRadians(startLat);
        endLat = Math.toRadians(endLat);

        double a = Math.pow(Math.sin(dLat / 2), 2) + Math.cos(startLat)
                * Math.cos(endLat) * Math.pow(Math.sin(dLong / 2), 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return Math.abs((EARTH_RADIUS * c)*1000); // <-- d

         */
        Location loc1 = new Location("");
        loc1.setLatitude(startLat);
        loc1.setLongitude(startLong);

        Location loc2 = new Location("");
        loc2.setLatitude(endLat);
        loc2.setLongitude(endLong);

        float distanceInMeters = loc1.distanceTo(loc2);
        return distanceInMeters;
    }
}
