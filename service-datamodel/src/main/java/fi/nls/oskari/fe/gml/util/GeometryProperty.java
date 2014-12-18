package fi.nls.oskari.fe.gml.util;

import com.vividsolutions.jts.geom.Geometry;

public class GeometryProperty {

    private Geometry geometry;

    public GeometryProperty() {

    }

    public GeometryProperty(Geometry value) {
        this.geometry = value;

    }

    @org.codehaus.jackson.annotate.JsonIgnore
    @com.fasterxml.jackson.annotation.JsonIgnore
    public Geometry getGeometry() {
        return geometry;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("WKT")
    @org.codehaus.jackson.annotate.JsonProperty("WKT")
    public String getWKT() {
        return geometry != null ? geometry.toText() : "";
    }

}