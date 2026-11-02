package it.breachato.fakeloc;

import android.content.Context;
import android.content.SharedPreferences;

/** Preset cities and the chosen place, kept in device-protected storage (read before unlock). */
final class Places {
    static final class Place {
        final String name, country;
        final double lat, lon;

        Place(String name, String country, double lat, double lon) {
            this.name = name;
            this.country = country;
            this.lat = lat;
            this.lon = lon;
        }
    }

    static final Place[] PRESETS = {
            new Place("Reykjavík", "Iceland", 64.14171, -21.92663),
            new Place("London", "United Kingdom", 51.50740, -0.12780),
            new Place("Paris", "France", 48.85660, 2.35220),
            new Place("Amsterdam", "Netherlands", 52.37300, 4.89260),
            new Place("Berlin", "Germany", 52.52000, 13.40500),
            new Place("Zurich", "Switzerland", 47.37690, 8.54170),
            new Place("Vienna", "Austria", 48.20820, 16.37380),
            new Place("Madrid", "Spain", 40.41680, -3.70380),
            new Place("Lisbon", "Portugal", 38.72230, -9.13930),
            new Place("Stockholm", "Sweden", 59.32930, 18.06860),
            new Place("Milano", "Italy", 45.46420, 9.19000),
            new Place("Rome", "Italy", 41.90280, 12.49640),
            new Place("New York", "United States", 40.71280, -74.00600),
            new Place("Los Angeles", "United States", 34.05220, -118.24370),
            new Place("Chicago", "United States", 41.87810, -87.62980),
            new Place("Dallas", "United States", 32.77670, -96.79700),
            new Place("Miami", "United States", 25.76170, -80.19180),
            new Place("Toronto", "Canada", 43.65320, -79.38320),
            new Place("Dubai", "United Arab Emirates", 25.20480, 55.27080),
            new Place("Tokyo", "Japan", 35.67620, 139.65030),
            new Place("Seoul", "South Korea", 37.56650, 126.97800),
            new Place("Singapore", "Singapore", 1.35210, 103.81980),
            new Place("Sydney", "Australia", -33.86880, 151.20930),
    };

    private Places() { }

    private static SharedPreferences prefs(Context c) {
        return c.createDeviceProtectedStorageContext().getSharedPreferences("place", Context.MODE_PRIVATE);
    }

    static Place current(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.contains("lat")) return PRESETS[0];
        return new Place(p.getString("name", "Custom"), p.getString("country", ""),
                Double.longBitsToDouble(p.getLong("lat", 0)), Double.longBitsToDouble(p.getLong("lon", 0)));
    }

    static void set(Context c, Place pl) {
        prefs(c).edit().putString("name", pl.name).putString("country", pl.country)
                .putLong("lat", Double.doubleToLongBits(pl.lat)).putLong("lon", Double.doubleToLongBits(pl.lon))
                .apply();
    }
}
