package org.geoavalanche.wps.buffer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;
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

public class Buffer extends StaticMethodsProcessFactory<Buffer> {

    private static final Logger LOG = Logger.getLogger(Buffer.class.getName());

    public Buffer() {
        super(Text.text("GeoAvalanche"), "geoavalanche", Buffer.class);
    }

    @DescribeProcess(title = "Buffer", description = "Buffer")
    @DescribeResult(description = "FeatureCollection")
    public static SimpleFeatureCollection Buffer(
            @DescribeParameter(name = "FeatureCollection", description = "FeatureCollection", min=1, max=1) SimpleFeatureCollection featureCollection,
            @DescribeParameter(name = "distance", description = "distance", min=0, max=1) Double distance,
            @DescribeParameter(name = "quadrantSegments", description = "quadrantSegments", min=0, max=1) Integer quadrantSegments,
            @DescribeParameter(name = "capStyle", description = "capStyle", min=0, max=1) Integer capStyle,
            @DescribeParameter(name = "sourceCRS", description = "sourceCRS", min=0, max=1) CoordinateReferenceSystem sourceCRS,
            @DescribeParameter(name = "targetCRS", description = "targetCRS", min=0, max=1) CoordinateReferenceSystem targetCRS
            ) throws Exception {
        
        List<SimpleFeature> featuresList = new ArrayList();
        SimpleFeatureBuilder fb = getSimpleFeatureBuilder(featureCollection);
        
        SimpleFeatureIterator itr = featureCollection.features();
        while (itr.hasNext()) {
            SimpleFeature feature = itr.next();
            if (feature.getDefaultGeometry() instanceof MultiLineString) {
                if (distance==null) distance=300.0;
                MultiLineString theGeometry = (MultiLineString)feature.getDefaultGeometry();
                for (int i=0; i<theGeometry.getNumGeometries(); i++) {
                    featuresList.addAll(buffer(fb, feature, (LineString)theGeometry.getGeometryN(i), distance, quadrantSegments, capStyle));
                }
                
            } else if (feature.getDefaultGeometry() instanceof LineString) {
                if (distance==null) distance=300.0;
                featuresList.addAll(buffer(fb, feature, (LineString) feature.getDefaultGeometry(), distance, quadrantSegments, capStyle));

            } else if (feature.getDefaultGeometry() instanceof Point) {
                if (distance==null) distance=600.0;
                featuresList.add(buffer(fb, feature, (Point) feature.getDefaultGeometry(), distance, quadrantSegments, capStyle));

            } else {
                featuresList.add(feature);
            }
        }

        SimpleFeatureCollection ret = new ListFeatureCollection(fb.getFeatureType(), transform(featuresList,sourceCRS,targetCRS));
        LOG.info("nrec = " + ret.size());
        return ret;
    }

    static List<SimpleFeature> transform(List<SimpleFeature> featuresList, CoordinateReferenceSystem sourceCRS, CoordinateReferenceSystem targetCRS) throws Exception {
        if (sourceCRS == null | targetCRS == null | sourceCRS==targetCRS) return featuresList;
        List<SimpleFeature> ret = new ArrayList();
        boolean lenient = true;
        MathTransform transform = CRS.findMathTransform(sourceCRS, targetCRS, lenient);
        for (SimpleFeature feature : featuresList) {
            if (feature.getDefaultGeometry()!=null) {
                feature.setDefaultGeometry(JTS.transform((Geometry)feature.getDefaultGeometry(), transform));
            } else {
                feature.setAttribute("the_geom", JTS.transform((Geometry)feature.getAttribute("the_geom"), transform));
            }
            ret.add(feature);
        }
        return ret;
    }
    
    static List<SimpleFeature> buffer(SimpleFeatureBuilder fb, SimpleFeature feature, LineString theGeometry, Double distance, Integer quadrantSegments, Integer capStyle) {       
        int _quadrantSegments = quadrantSegments == null ? 10 : quadrantSegments.intValue();
        int _capStyle = capStyle == null ? BufferParameters.CAP_ROUND : capStyle.intValue();
        List<SimpleFeature> featuresList = new ArrayList();
        GeometryFactory theGeometryFactory = new GeometryFactory(theGeometry.getPrecisionModel(), theGeometry.getSRID());
        for (int x = 1; x < theGeometry.getCoordinates().length; x++) {
            Coordinate[] points = new Coordinate[]{theGeometry.getCoordinates()[x - 1], theGeometry.getCoordinates()[x]};
            LineString newLineString = theGeometryFactory.createLineString(points);
            Polygon thePolygon = (Polygon) newLineString.buffer(distance, _quadrantSegments, _capStyle);
            MultiPolygon theMultiPolygon = new MultiPolygon(new Polygon[]{thePolygon}, thePolygon.getFactory());
            fb.reset();
            for (Property p : feature.getProperties()) {
                if (!p.getName().getLocalPart().equalsIgnoreCase("geometry")) {
                    fb.set(p.getName().getLocalPart(), p.getValue());
                }
            }
            fb.set("the_geom", theMultiPolygon);
            featuresList.add(fb.buildFeature(feature.getID() + "." + x));
        }
        return featuresList;
    }

    static SimpleFeature buffer(SimpleFeatureBuilder fb, SimpleFeature feature, Point theGeometry, Double distance, Integer quadrantSegments, Integer capStyle) {
        int _quadrantSegments = quadrantSegments == null ? 10 : quadrantSegments.intValue();
        int _capStyle = capStyle == null ? BufferParameters.CAP_ROUND : capStyle.intValue();
        Polygon thePolygon = (Polygon) theGeometry.buffer(distance, _quadrantSegments, _capStyle);
        MultiPolygon theMultiPolygon = new MultiPolygon(new Polygon[] {thePolygon}, thePolygon.getFactory());
        fb.reset();
        for (Property p : feature.getProperties()) {
            if (!p.getName().getLocalPart().equalsIgnoreCase("geometry")) {
                fb.set(p.getName().getLocalPart(), p.getValue());                            
            }
        }
        fb.set("the_geom", theMultiPolygon);
        return fb.buildFeature(feature.getID());
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
