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
import org.geotools.geometry.jts.JTS;
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
                fb.set("incidents", getNumIncidentsOfGeometry(incidents, theGeometry));
            } 
            featuresList.add(fb.buildFeature(feature.getID()));
        }
        
        ListFeatureCollection ret = new ListFeatureCollection(fb.getFeatureType(), featuresList);
        LOG.info("return=" + ret);
        return ret;
    }

    static int getNumIncidentsOfGeometry(List<Incidents> incidents, Geometry theGeometry) throws Exception {
        boolean lenient = true;
        MathTransform transform = CRS.findMathTransform(CRS.decode("EPSG:3857"), CRS.decode("EPSG:4326"), lenient);
        Geometry theGeometryWGS84 = JTS.transform(theGeometry, transform);
        
        GeometryFactory theGeometryFactory = new GeometryFactory(theGeometryWGS84.getPrecisionModel(), theGeometryWGS84.getSRID());            
        int _incidents = 0;
        for (Incidents entry: incidents) {
            Coordinate theCoordinate = new Coordinate(entry.incident.getLongitude(), entry.incident.getLatitude());
            Point thePoint = theGeometryFactory.createPoint(theCoordinate);
            if (theGeometryWGS84.contains(thePoint)) {
                _incidents++;
            }
        }        
        return _incidents;
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
        incidentstask.setOrderField("incidentid");
        List<Incidents> incidents = incidentstask.all();
        if (incidents==null) {
            incidents = new ArrayList();
        }
        return incidents;
    }
}
