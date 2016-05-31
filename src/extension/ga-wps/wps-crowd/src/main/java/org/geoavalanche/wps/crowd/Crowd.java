package org.geoavalanche.wps.crowd;

import com.ushahidi.java.sdk.UshahidiApi;
import com.ushahidi.java.sdk.api.Incident;
import com.ushahidi.java.sdk.api.Incidents;
import com.ushahidi.java.sdk.api.tasks.IncidentsTask;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import java.util.HashMap;
import java.util.Map;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;

import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.DirectPosition2D;
import org.geotools.process.factory.DescribeParameter;
import org.geotools.process.factory.DescribeProcess;
import org.geotools.process.factory.DescribeResult;
import org.geotools.process.factory.StaticMethodsProcessFactory;
import org.geotools.referencing.CRS;
import org.geotools.text.Text;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;

public class Crowd extends StaticMethodsProcessFactory<Crowd> {

    private static final Logger LOG = Logger.getLogger(Crowd.class.getName());

    public Crowd() {
        super(Text.text("GeoAvalanche"), "geoavalanche", Crowd.class);
    }

    @DescribeProcess(title = "Crowd", description = "Crowd")
    @DescribeResult(description = "FeatureCollection")
    public static SimpleFeatureCollection Crowd(
            @DescribeParameter(name = "FeatureCollection", description = "FeatureCollection") SimpleFeatureCollection featureCollection
    ) throws Exception {
        
        List<Incidents> incidents = getAllIncidents();
        
        List<SimpleFeature> featuresList = new ArrayList();
        SimpleFeatureBuilder fb = getSimpleFeatureBuilder(featureCollection);
        
        SimpleFeatureIterator itr = featureCollection.features();
        while (itr.hasNext()) {         
            SimpleFeature feature = itr.next();
            fb.reset();
            for (Property p: feature.getProperties()) {
               fb.set(p.getName().getLocalPart(), p.getValue());
            }
            Geometry theGeometry = (Geometry)feature.getAttribute("the_geom");
            if (theGeometry == null) {
                theGeometry = (Geometry)feature.getAttribute("geometry");
            }
            if (theGeometry != null) {
                GeometryFactory theGeometryFactory = new GeometryFactory(theGeometry.getPrecisionModel(), theGeometry.getSRID());            
                int _incidents = 0;
                for (Incidents entry: incidents) {
                    Coordinate theCoordinate = transform("EPSG:4326", "EPSG:3857", entry.incident.getLongitude(), entry.incident.getLatitude());
                    Point thePoint = theGeometryFactory.createPoint(theCoordinate);
                    if (theGeometry.contains(thePoint)) {
                        _incidents++;
                    }
                }
                fb.set("incidents", _incidents);
            } 
            featuresList.add(fb.buildFeature(feature.getID()));
        }
        
        ListFeatureCollection ret = new ListFeatureCollection(fb.getFeatureType(), featuresList);
        LOG.info("return=" + ret);
        return ret;
    }

    static SimpleFeatureBuilder getSimpleFeatureBuilder(SimpleFeatureCollection featureCollection) {
        Map<String, Class> theProperties = new HashMap();
        SimpleFeatureIterator itr = featureCollection.features();
        while (itr.hasNext()) {         
            SimpleFeature feature = itr.next();
            for (Property p: feature.getProperties()) {
               theProperties.put(p.getName().getLocalPart(), p.getType().getBinding());
            }
        }
        theProperties.put("incidents", Integer.class);

        SimpleFeatureTypeBuilder tb = new SimpleFeatureTypeBuilder();
        tb.setName(featureCollection.getSchema().getName());
        tb.setCRS(featureCollection.getSchema().getCoordinateReferenceSystem());
        for (Map.Entry<String, Class> entry : theProperties.entrySet()) {
            tb.add(entry.getKey(), entry.getValue());
        }
        SimpleFeatureType schema = tb.buildFeatureType();
        SimpleFeatureBuilder fb = new SimpleFeatureBuilder(schema);        
        return fb;
    }
    
    static List<Incidents> getAllIncidents() {
        UshahidiApi ushahidi = new UshahidiApi("http://geoavalanche.org/incident");        
        IncidentsTask incidentstask = ushahidi.factory.createIncidentsTask();
        incidentstask.setLimit(100);
        List<Incidents> incidents = incidentstask.all();
        if (incidents==null) {
            incidents = new ArrayList();
            Incidents i = new Incidents();
            i.incident = new Incident();
            i.incident.setLatitude(42.473304);
            i.incident.setLongitude(12.997361);
            incidents.add(i);
        }
        return incidents;
    }
    
    public static Coordinate transform(String from, String to, double longitude, double latitude) throws Exception {
        double xy[] = {longitude, latitude};
        Coordinate c = new Coordinate(longitude, latitude);
        
        if (from == null || to == null) {
            return c;
        }

        if (from.compareToIgnoreCase("EPSG:4326") == 0) {
            // E6 support
            if (xy[0] > 181 || xy[0] < -181) {
                xy[0] /= 1000000;
            }
            if (xy[1] > 91 || xy[1] < -91) {
                xy[1] /= 1000000;
            }
        }

        if (from.equals(to)) {
            return c;
        }

        CoordinateReferenceSystem from_crs = CRS.decode(from);

        CoordinateReferenceSystem to_crs = CRS.decode(to, true); //true=longitude first

        MathTransform transform1 = CRS.findMathTransform(from_crs, to_crs, false);

        DirectPosition2D from_point = new DirectPosition2D(xy[0], xy[1]);
        DirectPosition2D to_point = new DirectPosition2D(0, 0);

        transform1.transform(from_point, to_point);

        return new Coordinate(to_point.x, to_point.y);
    }
}
