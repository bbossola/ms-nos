package com.workshare.msnos.core.geo;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.maxmind.geoip2.model.OmniResponse;
import com.maxmind.geoip2.record.AbstractNamedRecord;
import com.maxmind.geoip2.record.City;
import com.maxmind.geoip2.record.Continent;
import com.maxmind.geoip2.record.Country;
import com.maxmind.geoip2.record.Subdivision;
import com.workshare.msnos.core.geo.Location.Place.Type;
import com.workshare.msnos.core.protocols.ip.Endpoint;
import com.workshare.msnos.soup.json.Json;

public class Location {
    private static final Logger log = LoggerFactory.getLogger(Location.class);

    public static Location UNKNOWN = new Location(Place.NOWHERE, Place.NOWHERE, Place.NOWHERE, Place.NOWHERE);

    public class Match {

        private final int value;
        private final Location source;
        private final Location target;

        public Match(Location source, Location target) {
            this.source = source;
            this.target = target;
            this.value = calculateValue(source, target);
        }

        private int calculateValue(Location source, Location target) {
            int total = 0;
            total += source.continent.equals(target.continent) ? 1 : 0;
            total += source.country.equals(target.country) ? 2 : 0;
            total += source.region.equals(target.region) ? 4 : 0;
            total += source.city.equals(target.city) ? 8 : 0;
            return total;
        }

        public Location getSource() {
            return source;
        }

        public Location getTarget() {
            return target;
        }

        public int value() {
            return value;
        }
        
        public String toString() {
            return "match: "+Integer.toString(value);
        }
    }

    public static class GPS {
        @JsonProperty("lat")
        private final Double latitude;
        @JsonProperty("lon")
        private final Double longitude;
        @JsonProperty("acc")
        private final Integer accuracy;
        
        public GPS(com.maxmind.geoip2.record.Location maxlocation) {
            this.latitude = maxlocation.getLatitude();
            this.longitude = maxlocation.getLongitude();
            this.accuracy = maxlocation.getAccuracyRadius();
        }

        public Double getLatitude() {
            return latitude;
        }

        public Double getLongitude() {
            return longitude;
        }

        public Integer getAccuracy() {
            return accuracy;
        }
    }
    
    public static class Place {
        public enum Type {
            CONTINENT, COUNTRY, REGION, CITY
        }

        public static Location.Place NOWHERE = new Location.Place(Type.CONTINENT, "Nowhere", "NN") {
            @Override
            public boolean equals(Object obj) {
                return false;
            }
            
            @Override
            public String toString() {
                return "{\"type\":\"UNKNOWN\"}";
            }
            
        };

        private final Type type;
        private final String name;
        private final String code;

        public Place(Type type, String name, String code) {
            if (type == null || code == null || name == null)
                throw new IllegalArgumentException("No constructor parameter can be null!");

            this.type = type;
            this.code = code;
            this.name = name;
        }

        public Type getType() {
            return type;
        }

        public String getName() {
            return name;
        }

        public String getCode() {
            return code;
        }

        @Override
        public String toString() {
            return Json.toJsonString(this);
        }

        @Override
        public int hashCode() {
            final int prime = 17;
            int result = 1;
            result = prime * result + code.hashCode();
            result = prime * result + type.hashCode();
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            try {
                Place other = (Place) obj;
                return type == other.type && code.equals(other.code);
            } catch (Exception ignore) {
                return false;
            }
        }
    }

    private final Place continent;
    private final Place country;
    private final Place region;
    private final Place city;
    private final int precision;
    private final GPS gps;

    public Location(OmniResponse response) {
        this(
            makeContinent(response.getContinent()),
            makeCountry(response.getCountry()),
            makeRegion(response.getMostSpecificSubdivision()),
            makeCity(response.getCity()),
            makeGps(response)
            );
    }

    public Location(Place continent, Place country, Place region, Place city) {
        this(continent, country, region, city, null);
    }
    
    public Location(Place continent, Place country, Place region, Place city, GPS gps) {
        this.continent =  parseNull(continent);
        this.country = parseNull(country);
        this.region = parseNull(region);
        this.city = parseNull(city);
        this.precision = computePrecision();
        this.gps = gps;
    }
    
    private Place parseNull(Place place) {
        return place == null ? Place.NOWHERE : place;
    }

    private int computePrecision() {
        int total = 0;
        total += (continent != Place.NOWHERE ? 1 : 0);
        total += (country != Place.NOWHERE ? 2 : 0);
        total += (region != Place.NOWHERE ? 4 : 0);
        total += (city != Place.NOWHERE ? 8 : 0);
        return total;
    }

    public Place getContinent() {
        return continent;
    }

    public Place getCountry() {
        return country;
    }

    public Place getRegion() {
        return region;
    }

    public Place getCity() {
        return city;
    }

    public GPS getGPS() {
        return gps;
    }

    public int getPrecision() {
        return precision;
    }

    public Match match(Location other) {
        return new Match(this, other == null ? UNKNOWN : other);
    }
    
    @Override
    public boolean equals(Object obj) {
        try {
            Location other = (Location) obj;
            return equals(continent, other.continent) && equals(country, other.country) && equals(region, other.region) && equals(city, other.city);    
        } catch (Exception ignore) {
            return false;
        }
    }

    @Override
    public int hashCode() {
        int result = continent != null ? continent.hashCode() : 0;
        result = 31 * result + (country != null ? country.hashCode() : 0);
        result = 31 * result + (region != null ? region.hashCode() : 0);
        result = 31 * result + (city != null ? city.hashCode() : 0);
        result = 31 * result + precision;
        return result;
    }

    private boolean equals(Place alfa, Place beta) {
        return alfa == beta || alfa.equals(beta);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (continent != Place.NOWHERE)
            appendPlaceForToString(sb, continent.getName());
        
        if (country != Place.NOWHERE)
            appendPlaceForToString(sb, country.getName());

        if (region != Place.NOWHERE)
            appendPlaceForToString(sb, region.getName());

        if (city != Place.NOWHERE)
            appendPlaceForToString(sb, city.getName());

        if (sb.length() == 0)
            sb.append("unknown");

        return sb.toString();
    }

    private void appendPlaceForToString(StringBuilder sb, String name) {
        if (sb.length() > 0) {
            sb.append(", ");
        }
            
        sb.append(name);
    }

    public static Location computeMostPreciseLocation(final Set<Endpoint> endpoints) {

        log.debug("Computing location for endpoints {}", endpoints);
        Location res = UNKNOWN;
        if (endpoints != null)
            for (Endpoint endpoint : endpoints) {
                final String hostString = endpoint.getNetwork().getHostString();
                Location loc = computeLocation(hostString);
                log.debug("Location for endpoints {}: [{}]", hostString, loc);
                if (loc.getPrecision() > res.getPrecision())
                    res = loc;
            }
        
        log.debug("Location is: ", res);
        return res;
    }

    public static Location computeLocation(final String hostString) {
        return LocationFactory.DEFAULT.make(hostString);
    }

    private static boolean isValid(final AbstractNamedRecord r) {
        return r != null && r.getGeoNameId() != null;
    }

    private static Place makeContinent(final Continent where) {
        return isValid(where) ? new Place(Place.Type.CONTINENT, where.getName(), where.getCode()) : null;
    }

    private static Place makeCountry(final Country where) {
        return isValid(where) ? new Place(Place.Type.COUNTRY, where.getName(), where.getIsoCode()) : null;
    }

    private static Place makeRegion(final Subdivision where) {
        if (!isValid(where))
            return null;
        
        String code = where.getIsoCode();
        if (code == null)
            code = where.getGeoNameId().toString();
            
        return newPlace(Place.Type.REGION, where.getName(), code);
    }

    private static GPS makeGps(OmniResponse response) {
        com.maxmind.geoip2.record.Location location = response.getLocation();
        return (location == null ? null : new GPS(location));
    }

    private static Place makeCity(final City where) {
        return isValid(where) ? new Place(Place.Type.CITY, where.getName(), where.getGeoNameId().toString()) : null;
    }

    private static Place newPlace(Type aRegion, String aName, String aIsoCode) {
        try {
            return new Place(aRegion, aName, aIsoCode);
        } catch (Throwable any) {
            return null;
        }
    }


}
