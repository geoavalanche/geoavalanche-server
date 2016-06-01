package org.geoavalanche.wps.buffer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.operation.buffer.BufferParameters;
import java.util.HashMap;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;

import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.process.factory.DescribeParameter;
import org.geotools.process.factory.DescribeProcess;
import org.geotools.process.factory.DescribeResult;
import org.geotools.process.factory.StaticMethodsProcessFactory;
import org.geotools.text.Text;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

public class Buffer extends StaticMethodsProcessFactory<Buffer> {

    public static final double DISTANCE = 5000;
    public static final int QUANDRANTSEGMENT = 100;
    public static final int CAP_STYLE = BufferParameters.CAP_ROUND;
    private static final Logger LOG = Logger.getLogger(Buffer.class.getName());

    public Buffer() {
        super(Text.text("GeoAvalanche"), "geoavalanche", Buffer.class);
    }

    @DescribeProcess(title = "Buffer", description = "Buffer")
    @DescribeResult(description = "FeatureCollection")
    public static SimpleFeatureCollection Buffer(
            @DescribeParameter(name = "FeatureCollection", description = "FeatureCollection", min=1, max=1) SimpleFeatureCollection featureCollection,
            @DescribeParameter(name = "distance", description = "distance", min=0, max=1, defaultValue = "org.geoavalanche.wps.buffer.Buffer#DISTANCE") double distance,
            @DescribeParameter(name = "quadrantSegments", description = "quadrantSegments", min=0, max=1, defaultValue = "org.geoavalanche.wps.buffer.Buffer#QUANDRANTSEGMENT") int quadrantSegments,
            @DescribeParameter(name = "capStyle", description = "capStyle", min=0, max=1, defaultValue = "org.geoavalanche.wps.buffer.Buffer#CAP_STYLE") int capStyle
            ) throws Exception {
        
        List<SimpleFeature> featuresList = new ArrayList<SimpleFeature>();
        SimpleFeatureBuilder fb = getSimpleFeatureBuilder(featureCollection);
        
        SimpleFeatureIterator itr = featureCollection.features();
        while (itr.hasNext()) {
            SimpleFeature feature = itr.next();
            if (feature.getDefaultGeometry() instanceof LineString) {
                LineString theGeometry = (LineString) feature.getDefaultGeometry();
                GeometryFactory theGeometryFactory = new GeometryFactory(theGeometry.getPrecisionModel(), theGeometry.getSRID());
                for (int x = 1; x < theGeometry.getCoordinates().length; x++) {
                    Coordinate[] points = new Coordinate[]{theGeometry.getCoordinates()[x - 1], theGeometry.getCoordinates()[x]};
                    LineString newLineString = theGeometryFactory.createLineString(points);
                    Polygon thePolygon = (Polygon) newLineString.buffer(distance, quadrantSegments, capStyle);
                    MultiPolygon theMultiPolygon = new MultiPolygon(new Polygon[] {thePolygon}, thePolygon.getFactory());
                    fb.reset();
                    for (Property p : feature.getProperties()) {
                        if (!p.getName().getLocalPart().equalsIgnoreCase("geometry")) {
                            fb.set(p.getName().getLocalPart(), p.getValue());                            
                        }
                    }
                    fb.set("the_geom", theMultiPolygon);
                    featuresList.add(fb.buildFeature(feature.getID() + "." + x));
                }
            } else if (feature.getDefaultGeometry() instanceof Point) {
                Point theGeometry = (Point) feature.getDefaultGeometry();
                Polygon thePolygon = (Polygon) theGeometry.buffer(distance, quadrantSegments, capStyle);
                MultiPolygon theMultiPolygon = new MultiPolygon(new Polygon[] {thePolygon}, thePolygon.getFactory());
                fb.reset();
                for (Property p : feature.getProperties()) {
                    if (!p.getName().getLocalPart().equalsIgnoreCase("geometry")) {
                        fb.set(p.getName().getLocalPart(), p.getValue());                            
                    }
                }
                fb.set("the_geom", theMultiPolygon);
                featuresList.add(fb.buildFeature(feature.getID()));
            } else {
                featuresList.add(feature);
            }
        }

        SimpleFeatureCollection ret = new ListFeatureCollection(fb.getFeatureType(), featuresList);
        LOG.info("return=" + ret);
        return ret;
    }

    static SimpleFeatureBuilder getSimpleFeatureBuilder(SimpleFeatureCollection featureCollection) {
        Map<String, Class> theProperties = new HashMap();
        SimpleFeatureIterator itr = featureCollection.features();
        while (itr.hasNext()) {
            SimpleFeature feature = itr.next();
            for (Property p : feature.getProperties()) {
                theProperties.put(p.getName().getLocalPart(), p.getType().getBinding());
            }
        }
        theProperties.put("the_geom", MultiPolygon.class);

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

}